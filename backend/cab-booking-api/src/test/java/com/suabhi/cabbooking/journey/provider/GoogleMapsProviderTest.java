package com.suabhi.cabbooking.journey.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.suabhi.cabbooking.journey.config.GoogleMapsProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class GoogleMapsProviderTest {
    private static final String API_KEY = "test-only-key";

    @Test
    void autocompleteMapsPlacePredictionsWithoutInventingCoordinates() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GoogleMapsProvider provider = provider(builder);

        server.expect(requestTo("https://places.googleapis.com/v1/places:autocomplete"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Goog-Api-Key", API_KEY))
                .andExpect(header("X-Goog-FieldMask",
                        org.hamcrest.Matchers.containsString("suggestions.placePrediction.placeId")))
                .andExpect(jsonPath("$.input").value("Shivajinagar"))
                .andExpect(jsonPath("$.includedRegionCodes[0]").value("in"))
                .andExpect(jsonPath("$.locationBias.circle.center.latitude").value(18.5204))
                .andRespond(withSuccess("""
                        {
                          "suggestions": [{
                            "placePrediction": {
                              "placeId": "ChIJ-pune-place",
                              "text": {"text": "Shivajinagar, Pune, Maharashtra, India"},
                              "structuredFormat": {"mainText": {"text": "Shivajinagar"}}
                            }
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<GeocodingProvider.PlaceSuggestion> suggestions =
                provider.search("Shivajinagar", City.PUNE);

        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.getFirst().providerReference()).isEqualTo("ChIJ-pune-place");
        assertThat(suggestions.getFirst().displayName()).isEqualTo("Shivajinagar");
        assertThat(suggestions.getFirst().formattedAddress())
                .isEqualTo("Shivajinagar, Pune, Maharashtra, India");
        assertThat(suggestions.getFirst().latitude()).isNull();
        assertThat(suggestions.getFirst().longitude()).isNull();
        server.verify();
    }

    @Test
    void resolvesSelectedPlaceThroughPlacesDetails() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GoogleMapsProvider provider = provider(builder);

        server.expect(requestTo("https://places.googleapis.com/v1/places/ChIJ-pune-place"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Goog-Api-Key", API_KEY))
                .andExpect(header("X-Goog-FieldMask", "id,formattedAddress,location"))
                .andRespond(withSuccess("""
                        {
                          "id": "ChIJ-pune-place",
                          "formattedAddress": "Shivajinagar, Pune, Maharashtra, India",
                          "location": {"latitude": 18.5308, "longitude": 73.8475}
                        }
                        """, MediaType.APPLICATION_JSON));

        GeocodingProvider.ResolvedPlace place = provider.resolve(
                new GeocodingProvider.PlaceInput(
                        "Shivajinagar, Pune", "ChIJ-pune-place", null, null));

        assertThat(place.formattedAddress()).isEqualTo("Shivajinagar, Pune, Maharashtra, India");
        assertThat(place.latitude()).isEqualTo(18.5308);
        assertThat(place.longitude()).isEqualTo(73.8475);
        server.verify();
    }

    @Test
    void geocodesAnAddressWhenNoPlaceReferenceIsProvided() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GoogleMapsProvider provider = provider(builder);

        server.expect(request -> {
                    String uri = request.getURI().toString();
                    assertThat(uri).contains("https://maps.googleapis.com/maps/api/geocode/json?");
                    assertThat(uri).contains("address=Pune%20Airport");
                    assertThat(uri).contains("region=in");
                    assertThat(uri).contains("key=" + API_KEY);
                })
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "status": "OK",
                          "results": [{
                            "formatted_address": "Pune Airport, Lohegaon, Pune, Maharashtra, India",
                            "place_id": "ChIJ-airport",
                            "geometry": {"location": {"lat": 18.5793, "lng": 73.9089}}
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        GeocodingProvider.ResolvedPlace place = provider.resolve(
                new GeocodingProvider.PlaceInput("Pune Airport", null, null, null));

        assertThat(place.providerReference()).isEqualTo("ChIJ-airport");
        assertThat(place.latitude()).isEqualTo(18.5793);
        assertThat(place.longitude()).isEqualTo(73.9089);
        server.verify();
    }

    @Test
    void calculatesDrivingRouteWithOrderedIntermediatesAndServerMetrics() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GoogleMapsProvider provider = provider(builder);

        server.expect(requestTo("https://routes.googleapis.com/directions/v2:computeRoutes"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Goog-Api-Key", API_KEY))
                .andExpect(header("X-Goog-FieldMask",
                        "routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline"))
                .andExpect(jsonPath("$.travelMode").value("DRIVE"))
                .andExpect(jsonPath("$.intermediates[0].location.latLng.latitude").value(18.5308))
                .andExpect(jsonPath("$.intermediates[1].location.latLng.latitude").value(18.7500))
                .andExpect(jsonPath("$.destination.location.latLng.longitude").value(72.8777))
                .andRespond(withSuccess("""
                        {
                          "routes": [{
                            "distanceMeters": 135000,
                            "duration": "7200.2s",
                            "polyline": {"encodedPolyline": "encoded-route"}
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        RouteProvider.RouteResult route = provider.calculate(new RouteProvider.RouteRequest(
                ServiceType.OUTSTATION,
                City.PUNE,
                Instant.parse("2027-01-15T03:00:00Z"),
                List.of(
                        new GeocodingProvider.ResolvedPlace("Pune", "pune", 18.5204, 73.8567),
                        new GeocodingProvider.ResolvedPlace("Shivajinagar", "shivajinagar", 18.5308, 73.8475),
                        new GeocodingProvider.ResolvedPlace("Lonavala", "lonavala", 18.7500, 73.4060),
                        new GeocodingProvider.ResolvedPlace("Mumbai", "mumbai", 19.0760, 72.8777)
                )
        ));

        assertThat(route.distanceMeters()).isEqualTo(135000);
        assertThat(route.durationSeconds()).isEqualTo(7201);
        assertThat(route.encodedPolyline()).isEqualTo("encoded-route");
        assertThat(route.providerReference()).isNull();
        server.verify();
    }

    @Test
    void mapsProviderAuthorizationErrorsToServiceUnavailableWithoutLeakingKey() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GoogleMapsProvider provider = provider(builder);

        server.expect(requestTo("https://places.googleapis.com/v1/places:autocomplete"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"API key denied\"}}"));

        assertThatThrownBy(() -> provider.search("Pune Airport", City.PUNE))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.getReason()).doesNotContain(API_KEY);
                });
        server.verify();
    }

    private static GoogleMapsProvider provider(RestClient.Builder builder) {
        GoogleMapsProperties properties = new GoogleMapsProperties(
                true, API_KEY, Duration.ofSeconds(1), Duration.ofSeconds(1), 100, 5);
        return new GoogleMapsProvider(builder.build(), properties);
    }
}
