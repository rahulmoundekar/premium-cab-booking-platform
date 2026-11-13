package com.suabhi.cabbooking.journey.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.suabhi.cabbooking.journey.provider.City;
import com.suabhi.cabbooking.journey.provider.GeocodingProvider;
import com.suabhi.cabbooking.journey.provider.RouteProvider;
import com.suabhi.cabbooking.journey.provider.ServiceType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class JourneyProviderConfigurationTest {
    private final JourneyProviderConfiguration configuration = new JourneyProviderConfiguration();

    @Test
    void unconfiguredGeocodingProviderFailsExplicitly() {
        GeocodingProvider provider = configuration.unconfiguredGeocodingProvider();

        assertThatThrownBy(() -> provider.search("Pune Airport", City.PUNE))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void unconfiguredRouteProviderFailsExplicitly() {
        RouteProvider provider = configuration.unconfiguredRouteProvider();
        RouteProvider.RouteRequest request = new RouteProvider.RouteRequest(
                ServiceType.OUTSTATION,
                City.PUNE,
                Instant.parse("2027-01-15T03:00:00Z"),
                List.of(
                        new GeocodingProvider.ResolvedPlace("Pune", "pune-ref", 18.5204, 73.8567),
                        new GeocodingProvider.ResolvedPlace("Mumbai", "mumbai-ref", 19.0760, 72.8777)
                )
        );

        assertThatThrownBy(() -> provider.calculate(request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }
}
