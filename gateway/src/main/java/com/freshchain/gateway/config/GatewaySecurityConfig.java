package com.freshchain.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Validation at the edge, against Asgardeo's published JWKS. Every service
 * behind this validates again — this is the first check, not the only one.
 */
@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class GatewaySecurityConfig {

    private final SecurityProperties properties;

    public GatewaySecurityConfig(SecurityProperties properties) {
        this.properties = properties;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        // A CORS preflight carries no credentials by design, so
                        // challenging it would fail every cross-origin call
                        // before the real request was ever made.
                        .pathMatchers(org.springframework.http.HttpMethod.OPTIONS).permitAll()
                        .pathMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .pathMatchers("/docs/**").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(reactiveConverter())))
                .build();
    }

    private ReactiveJwtAuthenticationConverterAdapter reactiveConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new JwtRoleConverter(properties.roleClaims()));
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }
}
