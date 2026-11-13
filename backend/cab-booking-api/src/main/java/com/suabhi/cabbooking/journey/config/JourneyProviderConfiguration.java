package com.suabhi.cabbooking.journey.config;

import com.suabhi.cabbooking.journey.provider.City;
import com.suabhi.cabbooking.journey.provider.GeocodingProvider;
import com.suabhi.cabbooking.journey.provider.RouteProvider;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Configuration(proxyBeanMethods = false)
public class JourneyProviderConfiguration {
    @Bean
    @ConditionalOnMissingBean(GeocodingProvider.class)
    GeocodingProvider unconfiguredGeocodingProvider() {
        return new GeocodingProvider() {
            @Override
            public List<PlaceSuggestion> search(String query, City city) {
                throw unavailable();
            }

            @Override
            public ResolvedPlace resolve(PlaceInput input) {
                throw unavailable();
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(RouteProvider.class)
    RouteProvider unconfiguredRouteProvider() {
        return request -> {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "A routing provider has not been configured.");
        };
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "A geocoding provider has not been configured.");
    }
}
