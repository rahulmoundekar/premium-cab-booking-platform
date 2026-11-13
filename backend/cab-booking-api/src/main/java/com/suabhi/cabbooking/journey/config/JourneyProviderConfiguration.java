package com.suabhi.cabbooking.journey.config;

import com.suabhi.cabbooking.journey.provider.City;
import com.suabhi.cabbooking.journey.provider.GeocodingProvider;
import com.suabhi.cabbooking.journey.provider.GoogleMapsProvider;
import com.suabhi.cabbooking.journey.provider.RouteProvider;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.server.ResponseStatusException;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GoogleMapsProperties.class)
public class JourneyProviderConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "app.maps.google", name = "enabled", havingValue = "true")
    GoogleMapsProvider googleMapsProvider(GoogleMapsProperties properties) {
        properties.validateEnabledConfiguration();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        RestClient restClient = RestClient.builder().requestFactory(requestFactory).build();
        return new GoogleMapsProvider(restClient, properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.maps.google", name = "enabled",
            havingValue = "false", matchIfMissing = true)
    @ConditionalOnMissingBean(GeocodingProvider.class)
    GeocodingProvider unconfiguredGeocodingProvider() {
        return new GeocodingProvider() {
            @Override
            public List<PlaceSuggestion> search(String query, City city) {
                throw unavailable("A geocoding provider has not been configured.");
            }

            @Override
            public ResolvedPlace resolve(PlaceInput input) {
                throw unavailable("A geocoding provider has not been configured.");
            }
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.maps.google", name = "enabled",
            havingValue = "false", matchIfMissing = true)
    @ConditionalOnMissingBean(RouteProvider.class)
    RouteProvider unconfiguredRouteProvider() {
        return request -> {
            throw unavailable("A routing provider has not been configured.");
        };
    }

    private static ResponseStatusException unavailable(String message) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
