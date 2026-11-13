package com.suabhi.cabbooking.journey.provider;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GeocodingPlaceInputTest {
    @Test
    void requiresLatitudeAndLongitudeTogether() {
        assertThatThrownBy(() ->
                new GeocodingProvider.PlaceInput("Pune Airport", null, 18.5793, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provided together");
    }

    @Test
    void rejectsOutOfRangeCoordinates() {
        assertThatThrownBy(() ->
                new GeocodingProvider.PlaceInput("Pune Airport", null, 95.0, 73.9))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Latitude");
    }
}
