package com.suabhi.cabbooking.common.security;

import com.suabhi.cabbooking.common.config.CorsProperties;
import com.suabhi.cabbooking.common.config.SecurityProperties;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableConfigurationProperties({CorsProperties.class, SecurityProperties.class})
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, CorsConfigurationSource corsSource,
                                             SecurityProperties securityProperties) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(cookie -> cookie.path("/").sameSite("Lax")
                .secure(securityProperties.csrfCookieSecure()));
        http.cors(cors -> cors.configurationSource(corsSource))
            .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                    .sessionFixation(fixation -> fixation.changeSessionId()))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/public/**").permitAll()
                    .anyRequest().authenticated())
            .headers(headers -> {
                headers.frameOptions(frame -> frame.deny());
                headers.contentTypeOptions(Customizer.withDefaults());
                headers.referrerPolicy(referrer -> referrer.policy(
                        org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy
                                .STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                if (securityProperties.hstsEnabled()) {
                    headers.httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true)
                            .maxAgeInSeconds(31_536_000));
                }
            });
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT,
                "X-Requested-With", "X-XSRF-TOKEN"));
        config.setExposedHeaders(List.of(HttpHeaders.LOCATION, HttpHeaders.CONTENT_DISPOSITION));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
