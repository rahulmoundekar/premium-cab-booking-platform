package com.suabhi.cabbooking.journey.provider;

import java.util.List;

/**
 * Provider-neutral geocoding port. Vendor SDK types must not cross this boundary.
 */
public interface GeocodingProvider {
    List<PlaceSuggestion> search(String query, City city);

    ResolvedPlace resolve(PlaceInput input);

    record PlaceInput(
            String formattedAddress,
            String providerReference,
            Double latitude,
            Double longitude
    ) {
        public PlaceInput {
            if (formattedAddress == null || formattedAddress.isBlank()) {
                throw new IllegalArgumentException("A formatted address is required.");
            }
            if ((latitude == null) != (longitude == null)) {
                throw new IllegalArgumentException("Latitude and longitude must be provided together.");
            }
            if (latitude != null && (!Double.isFinite(latitude) || latitude < -90.0 || latitude > 90.0)) {
                throw new IllegalArgumentException("Latitude must be a finite value between -90 and 90.");
            }
            if (longitude != null && (!Double.isFinite(longitude) || longitude < -180.0 || longitude > 180.0)) {
                throw new IllegalArgumentException("Longitude must be a finite value between -180 and 180.");
            }
        }
    }

    /**
     * Autocomplete suggestions do not necessarily include coordinates.
     * Coordinates are resolved server-side after the customer selects a suggestion.
     */
    record PlaceSuggestion(
            String providerReference,
            String displayName,
            String formattedAddress,
            Double latitude,
            Double longitude
    ) {
        public PlaceSuggestion {
            if (displayName == null || displayName.isBlank()
                    || formattedAddress == null || formattedAddress.isBlank()) {
                throw new IllegalArgumentException("A place suggestion must include a display name and address.");
            }
            if ((latitude == null) != (longitude == null)) {
                throw new IllegalArgumentException("Suggestion coordinates must be provided together.");
            }
            if (latitude != null) {
                validateCoordinates(latitude, longitude);
            }
        }
    }

    record ResolvedPlace(
            String formattedAddress,
            String providerReference,
            double latitude,
            double longitude
    ) {
        public ResolvedPlace {
            if (formattedAddress == null || formattedAddress.isBlank()) {
                throw new IllegalArgumentException("A resolved place must include a formatted address.");
            }
            validateCoordinates(latitude, longitude);
        }
    }

    private static void validateCoordinates(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitude must be a finite value between -90 and 90.");
        }
        if (!Double.isFinite(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitude must be a finite value between -180 and 180.");
        }
    }
}
