package com.suabhi.cabbooking.journey.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RouteResultTest {
    @Test
    void acceptsPositiveServerCalculatedMetrics() {
        RouteProvider.RouteResult result =
                new RouteProvider.RouteResult(1_250, 420, "encoded-line", "provider-route-1");

        assertThat(result.distanceMeters()).isEqualTo(1_250);
        assertThat(result.durationSeconds()).isEqualTo(420);
    }

    @Test
    void rejectsMissingOrNonPositiveDistance() {
        assertThatThrownBy(() -> new RouteProvider.RouteResult(0, 420, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("distance");
    }

    @Test
    void rejectsMissingOrNonPositiveDuration() {
        assertThatThrownBy(() -> new RouteProvider.RouteResult(1_250, 0, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duration");
    }
}
