package com.suabhi.cabbooking.journey.api;

import com.suabhi.cabbooking.journey.provider.GeocodingProvider;
import com.suabhi.cabbooking.journey.provider.RouteProvider;
import com.suabhi.cabbooking.journey.provider.ServiceType;
import java.time.Instant;
import java.util.List;

public record JourneyPreviewResponse(
        ServiceType serviceType,
        String originCity,
        Instant departureAt,
        List<Place> locations,
        long distanceMeters,
        long durationSeconds,
        String encodedPolyline,
        String routeReference
) {
    public JourneyPreviewResponse {
        locations = List.copyOf(locations);
    }

    public record Place(
            String formattedAddress,
            String providerReference,
            double latitude,
            double longitude
    ) {
        public static Place from(GeocodingProvider.ResolvedPlace place) {
            return new Place(place.formattedAddress(), place.providerReference(),
                    place.latitude(), place.longitude());
        }
    }

    public static JourneyPreviewResponse from(
            JourneyPreviewRequest request,
            List<GeocodingProvider.ResolvedPlace> locations,
            RouteProvider.RouteResult route
    ) {
        return new JourneyPreviewResponse(
                request.serviceType(),
                request.originCity().name(),
                request.departureAt().toInstant(),
                locations.stream().map(Place::from).toList(),
                route.distanceMeters(),
                route.durationSeconds(),
                route.encodedPolyline(),
                route.providerReference()
        );
    }
}
