package com.suabhi.cabbooking.journey.service;

import com.suabhi.cabbooking.journey.api.JourneyPreviewRequest;
import com.suabhi.cabbooking.journey.api.JourneyPreviewResponse;
import com.suabhi.cabbooking.journey.provider.GeocodingProvider;
import com.suabhi.cabbooking.journey.provider.RouteProvider;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class JourneyPreviewService {
    private final GeocodingProvider geocodingProvider;
    private final RouteProvider routeProvider;

    public JourneyPreviewService(GeocodingProvider geocodingProvider, RouteProvider routeProvider) {
        this.geocodingProvider = geocodingProvider;
        this.routeProvider = routeProvider;
    }

    public List<GeocodingProvider.PlaceSuggestion> searchPlaces(String query,
                                                                  com.suabhi.cabbooking.journey.provider.City city) {
        String normalizedQuery = query == null ? "" : query.strip();
        if (normalizedQuery.length() < 3 || normalizedQuery.length() > 160) {
            throw new IllegalArgumentException("Place search query must contain between 3 and 160 characters.");
        }
        if (city == null) {
            throw new IllegalArgumentException("An operating city is required for place search.");
        }
        return List.copyOf(geocodingProvider.search(normalizedQuery, city));
    }

    public JourneyPreviewResponse preview(JourneyPreviewRequest request) {
        List<GeocodingProvider.ResolvedPlace> locations = new ArrayList<>();
        locations.add(resolve(request.pickup()));
        for (JourneyPreviewRequest.PlaceRequest stop : request.stops()) {
            locations.add(resolve(stop));
        }
        locations.add(resolve(request.dropOff()));

        RouteProvider.RouteRequest routeRequest = new RouteProvider.RouteRequest(
                request.serviceType(),
                request.originCity(),
                request.departureAt().toInstant(),
                locations
        );
        RouteProvider.RouteResult route = routeProvider.calculate(routeRequest);
        return JourneyPreviewResponse.from(request, locations, route);
    }

    private GeocodingProvider.ResolvedPlace resolve(JourneyPreviewRequest.PlaceRequest place) {
        return geocodingProvider.resolve(place.toProviderInput());
    }
}
