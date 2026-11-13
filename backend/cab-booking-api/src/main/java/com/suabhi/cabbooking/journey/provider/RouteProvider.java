package com.suabhi.cabbooking.journey.provider;

import java.time.Instant;
import java.util.List;

/**
 * Provider-neutral routing port. Distance and duration must be calculated
 * server-side; clients cannot submit authoritative route metrics.
 */
public interface RouteProvider {
    RouteResult calculate(RouteRequest request);

    record RouteRequest(
            ServiceType serviceType,
            City originCity,
            Instant departureAt,
            List<GeocodingProvider.ResolvedPlace> locations
    ) {
        public RouteRequest {
            if (serviceType == null || originCity == null || departureAt == null) {
                throw new IllegalArgumentException("Service type, origin city and departure time are required.");
            }
            locations = List.copyOf(locations);
            if (locations.size() < 2) {
                throw new IllegalArgumentException("A route requires at least pickup and drop-off locations.");
            }
        }
    }

    record RouteResult(
            long distanceMeters,
            long durationSeconds,
            String encodedPolyline,
            String providerReference
    ) {
        public RouteResult {
            if (distanceMeters <= 0) {
                throw new IllegalArgumentException("Route distance must be greater than zero.");
            }
            if (durationSeconds <= 0) {
                throw new IllegalArgumentException("Route duration must be greater than zero.");
            }
        }
    }
}
