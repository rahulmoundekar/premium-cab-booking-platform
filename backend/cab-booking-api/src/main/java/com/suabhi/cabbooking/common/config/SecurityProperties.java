package com.suabhi.cabbooking.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(boolean csrfCookieSecure, boolean hstsEnabled) {
}
