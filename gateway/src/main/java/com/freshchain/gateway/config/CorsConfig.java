package com.freshchain.gateway.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * Cross-origin access for the browser client.
 *
 * <p>In the container the SPA is served by nginx, which proxies {@code /api} to
 * this gateway, so requests are same-origin and never reach the CORS machinery.
 * This exists for the other case: running the frontend with {@code npm run dev}
 * on Vite's own port while the rest of the stack is in Docker.
 *
 * <p>Origins are an explicit allowlist rather than a wildcard. Credentials are
 * enabled, and the CORS specification forbids {@code *} with credentials — but
 * the more important reason is that the wildcard is how a page nobody
 * scrutinised ends up talking to this API.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter(
            @Value("${freshchain.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(allowedOrigins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        cors.setExposedHeaders(List.of("Location"));
        cors.setAllowCredentials(true);
        // Cache the preflight so a busy page is not re-asking on every call.
        cors.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return new CorsWebFilter(source);
    }
}
