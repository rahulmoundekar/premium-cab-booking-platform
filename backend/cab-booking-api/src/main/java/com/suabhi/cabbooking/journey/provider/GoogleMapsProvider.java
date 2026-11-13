package com.suabhi.cabbooking.journey.provider;

import tools.jackson.databind.JsonNode;
import com.suabhi.cabbooking.journey.config.GoogleMapsProperties;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

/**
 * Google Maps Platform adapter for Places API (New), Geocoding API and Routes API.
 * All Google-specific request/response details stay inside this infrastructure adapter.
 */
public final class GoogleMapsProvider implements GeocodingProvider, RouteProvider {
    private static final String PLACES_AUTOCOMPLETE_URL =
            "https://places.googleapis.com/v1/places:autocomplete";
    private static final String PLACES_DETAILS_BASE_URL =
            "https://places.googleapis.com/v1/places/";
    private static final String GEOCODING_URL =
            "https://maps.googleapis.com/maps/api/geocode/json";
    private static final String ROUTES_URL =
            "https://routes.googleapis.com/directions/v2:computeRoutes";
    private static final String AUTOCOMPLETE_FIELD_MASK =
            "suggestions.placePrediction.placeId,"
                    + "suggestions.placePrediction.text.text,"
                    + "suggestions.placePrediction.structuredFormat.mainText.text";
    private static final String PLACE_DETAILS_FIELD_MASK = "id,formattedAddress,location";
    private static final String ROUTE_FIELD_MASK =
            "routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline";

    private final RestClient restClient;
    private final GoogleMapsProperties properties;
    private final RequestRateLimiter rateLimiter;

    public GoogleMapsProvider(RestClient restClient, GoogleMapsProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
        this.properties.validateEnabledConfiguration();
        this.rateLimiter = new RequestRateLimiter(properties.requestsPerSecond());
    }

    @Override
    public List<PlaceSuggestion> search(String query, City city) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("input", query);
        body.put("includedRegionCodes", List.of("in"));
        body.put("regionCode", "IN");
        body.put("languageCode", "en");
        body.put("locationBias", cityBias(city));

        JsonNode response = execute(() -> restClient.post()
                .uri(PLACES_AUTOCOMPLETE_URL)
                .header("X-Goog-Api-Key", properties.apiKey())
                .header("X-Goog-FieldMask", AUTOCOMPLETE_FIELD_MASK)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class));

        JsonNode suggestions = response == null ? null : response.path("suggestions");
        if (suggestions == null || !suggestions.isArray()) {
            return List.of();
        }

        List<PlaceSuggestion> results = new ArrayList<>();
        for (JsonNode suggestion : suggestions) {
            JsonNode prediction = suggestion.path("placePrediction");
            String placeId = text(prediction.path("placeId"));
            String formattedText = text(prediction.path("text").path("text"));
            if (placeId == null || formattedText == null) {
                continue;
            }

            String displayName = text(prediction.path("structuredFormat")
                    .path("mainText").path("text"));
            if (displayName == null) {
                displayName = formattedText;
            }
            results.add(new PlaceSuggestion(placeId, displayName, formattedText, null, null));
            if (results.size() >= properties.maxSuggestions()) {
                break;
            }
        }
        return List.copyOf(results);
    }

    @Override
    public ResolvedPlace resolve(PlaceInput input) {
        if (input.providerReference() != null && !input.providerReference().isBlank()) {
            return resolvePlaceId(input);
        }
        return geocode(input);
    }

    @Override
    public RouteResult calculate(RouteRequest request) {
        List<GeocodingProvider.ResolvedPlace> locations = request.locations();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("origin", waypoint(locations.getFirst()));
        body.put("destination", waypoint(locations.getLast()));
        if (locations.size() > 2) {
            body.put("intermediates", locations.subList(1, locations.size() - 1)
                    .stream().map(GoogleMapsProvider::waypoint).toList());
        }
        body.put("travelMode", "DRIVE");
        body.put("routingPreference", "TRAFFIC_AWARE");
        body.put("departureTime", request.departureAt().toString());
        body.put("computeAlternativeRoutes", false);
        body.put("routeModifiers", Map.of(
                "avoidTolls", false,
                "avoidHighways", false,
                "avoidFerries", false));
        body.put("languageCode", "en-IN");
        body.put("units", "METRIC");

        JsonNode response = execute(() -> restClient.post()
                .uri(ROUTES_URL)
                .header("X-Goog-Api-Key", properties.apiKey())
                .header("X-Goog-FieldMask", ROUTE_FIELD_MASK)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class));

        JsonNode routes = response == null ? null : response.path("routes");
        if (routes == null || !routes.isArray() || routes.isEmpty()) {
            throw badRequest("Google Maps could not calculate a route for the selected locations.");
        }

        JsonNode route = routes.get(0);
        long distanceMeters = route.path("distanceMeters").asLong(0);
        long durationSeconds = parseDurationSeconds(text(route.path("duration")));
        if (distanceMeters <= 0 || durationSeconds <= 0) {
            throw providerUnavailable();
        }
        String encodedPolyline = text(route.path("polyline").path("encodedPolyline"));
        return new RouteResult(distanceMeters, durationSeconds, encodedPolyline, null);
    }

    private ResolvedPlace resolvePlaceId(PlaceInput input) {
        String placeId = input.providerReference().strip();
        if (placeId.startsWith("places/")) {
            placeId = placeId.substring("places/".length());
        }
        if (placeId.isBlank() || placeId.contains("/")) {
            throw badRequest("The selected place reference is invalid.");
        }
        String encodedId = UriUtils.encodePathSegment(placeId, StandardCharsets.UTF_8);
        JsonNode response = execute(() -> restClient.get()
                .uri(PLACES_DETAILS_BASE_URL + encodedId)
                .header("X-Goog-Api-Key", properties.apiKey())
                .header("X-Goog-FieldMask", PLACE_DETAILS_FIELD_MASK)
                .retrieve()
                .body(JsonNode.class));

        if (response == null) {
            throw providerUnavailable();
        }
        String address = text(response.path("formattedAddress"));
        String resolvedId = text(response.path("id"));
        JsonNode location = response.path("location");
        if (address == null || location.isMissingNode()) {
            throw providerUnavailable();
        }
        return resolvedPlace(address, resolvedId == null ? placeId : resolvedId, location);
    }

    private ResolvedPlace geocode(PlaceInput input) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(GEOCODING_URL)
                .queryParam("region", "in")
                .queryParam("key", properties.apiKey());
        if (input.latitude() != null) {
            uri.queryParam("latlng", input.latitude() + "," + input.longitude());
        } else {
            uri.queryParam("address", input.formattedAddress());
        }
        URI requestUri = uri.build().encode().toUri();

        JsonNode response = execute(() -> restClient.get()
                .uri(requestUri)
                .retrieve()
                .body(JsonNode.class));
        if (response == null) {
            throw providerUnavailable();
        }

        String status = text(response.path("status"));
        if (!"OK".equals(status)) {
            if ("ZERO_RESULTS".equals(status) || "INVALID_REQUEST".equals(status)) {
                throw badRequest("Google Maps could not resolve the supplied address.");
            }
            throw providerUnavailable();
        }

        JsonNode results = response.path("results");
        if (!results.isArray() || results.isEmpty()) {
            throw badRequest("Google Maps could not resolve the supplied address.");
        }
        JsonNode result = results.get(0);
        String address = text(result.path("formatted_address"));
        String placeId = text(result.path("place_id"));
        JsonNode location = result.path("geometry").path("location");
        if (address == null || location.isMissingNode()) {
            throw providerUnavailable();
        }
        return resolvedPlace(address, placeId, location);
    }

    private static ResolvedPlace resolvedPlace(String address, String reference, JsonNode location) {
        double latitude = location.path("latitude").asDouble(Double.NaN);
        double longitude = location.path("longitude").asDouble(Double.NaN);
        // Geocoding API v3 uses lat/lng while Places API (New) uses latitude/longitude.
        if (!Double.isFinite(latitude)) {
            latitude = location.path("lat").asDouble(Double.NaN);
        }
        if (!Double.isFinite(longitude)) {
            longitude = location.path("lng").asDouble(Double.NaN);
        }
        if (address == null || !Double.isFinite(latitude) || !Double.isFinite(longitude)) {
            throw providerUnavailable();
        }
        return new ResolvedPlace(address, reference, latitude, longitude);
    }

    private static Map<String, Object> cityBias(City city) {
        double latitude;
        double longitude;
        double radiusMeters;
        if (city == City.MUMBAI) {
            latitude = 19.0760;
            longitude = 72.8777;
            radiusMeters = 45_000;
        } else {
            latitude = 18.5204;
            longitude = 73.8567;
            radiusMeters = 30_000;
        }
        return Map.of("circle", Map.of(
                "center", Map.of("latitude", latitude, "longitude", longitude),
                "radius", radiusMeters));
    }

    private static Map<String, Object> waypoint(GeocodingProvider.ResolvedPlace place) {
        return Map.of("location", Map.of("latLng", Map.of(
                "latitude", place.latitude(),
                "longitude", place.longitude())));
    }

    private <T> T execute(Supplier<T> request) {
        for (int attempt = 0; attempt < 2; attempt++) {
            rateLimiter.acquire();
            try {
                return request.get();
            } catch (RestClientResponseException exception) {
                int status = exception.getStatusCode().value();
                if (attempt == 0 && isTransientStatus(status)) {
                    pauseBeforeRetry();
                    continue;
                }
                if (status == 400 || status == 404) {
                    throw badRequest("Google Maps rejected or could not find the requested location.");
                }
                throw providerUnavailable();
            } catch (ResourceAccessException exception) {
                if (attempt == 0) {
                    pauseBeforeRetry();
                    continue;
                }
                throw providerUnavailable();
            } catch (RestClientException exception) {
                throw providerUnavailable();
            }
        }
        throw providerUnavailable();
    }

    private static boolean isTransientStatus(int status) {
        return status == 500 || status == 502 || status == 503 || status == 504;
    }

    private static void pauseBeforeRetry() {
        try {
            Thread.sleep(150L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw providerUnavailable();
        }
    }

    private static long parseDurationSeconds(String duration) {
        if (duration == null || !duration.endsWith("s")) {
            throw providerUnavailable();
        }
        try {
            return new BigDecimal(duration.substring(0, duration.length() - 1))
                    .setScale(0, java.math.RoundingMode.CEILING)
                    .longValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw providerUnavailable();
        }
    }

    private static String text(JsonNode value) {
        return value == null || value.isMissingNode() || value.isNull() || value.asText().isBlank()
                ? null : value.asText();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException providerUnavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "The Google Maps provider is temporarily unavailable.");
    }

    /**
     * Per-instance start-rate limiter. Configure the Google Cloud project quota as
     * the authoritative cross-instance limit when deploying multiple API replicas.
     */
    private static final class RequestRateLimiter {
        private final AtomicLong nextSlotNanos = new AtomicLong();
        private final long intervalNanos;

        private RequestRateLimiter(int requestsPerSecond) {
            intervalNanos = Duration.ofSeconds(1).toNanos() / requestsPerSecond;
        }

        private void acquire() {
            long now = System.nanoTime();
            long slot;
            while (true) {
                long previous = nextSlotNanos.get();
                slot = Math.max(now, previous);
                if (nextSlotNanos.compareAndSet(previous, slot + intervalNanos)) {
                    break;
                }
            }
            long waitNanos = slot - now;
            if (waitNanos > 0) {
                try {
                    TimeUnit.NANOSECONDS.sleep(waitNanos);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw providerUnavailable();
                }
            }
        }
    }
}
