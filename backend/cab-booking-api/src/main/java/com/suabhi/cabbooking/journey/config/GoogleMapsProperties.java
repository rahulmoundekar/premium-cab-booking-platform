package com.suabhi.cabbooking.journey.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Server-side Google Maps configuration. The API key is never returned by an API.
 */
@ConfigurationProperties(prefix = "app.maps.google")
public record GoogleMapsProperties(
        boolean enabled,
        String apiKey,
        Duration connectTimeout,
        Duration readTimeout,
        int requestsPerSecond,
        int maxSuggestions
) {
    public GoogleMapsProperties {
        apiKey = apiKey == null ? "" : apiKey.strip();
        connectTimeout = positive(connectTimeout, Duration.ofSeconds(3));
        readTimeout = positive(readTimeout, Duration.ofSeconds(8));
        requestsPerSecond = requestsPerSecond <= 0 ? 8 : Math.min(requestsPerSecond, 100);
        maxSuggestions = maxSuggestions <= 0 ? 5 : Math.min(maxSuggestions, 5);
    }

    public void validateEnabledConfiguration() {
        if (enabled && apiKey.isBlank()) {
            throw new IllegalStateException(
                    "GOOGLE_MAPS_API_KEY must be configured when GOOGLE_MAPS_ENABLED=true.");
        }
    }

    private static Duration positive(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }
}
