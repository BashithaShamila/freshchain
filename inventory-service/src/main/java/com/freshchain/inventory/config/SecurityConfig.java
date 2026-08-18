package com.freshchain.inventory.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The gateway already validated the token at the edge. This service validates it
 * again anyway, because a service must not trust the network it is sitting on —
 * anything that can reach port 8082 directly would otherwise be unauthenticated.
 *
 * <p>Tokens are issued by Asgardeo. Signature, issuer and expiry are checked
 * against the provider's published JWKS, and the audience is checked against
 * this deployment's client id via
 * {@code spring.security.oauth2.resourceserver.jwt.audiences} — without that, a
 * validly-signed token minted for some other application in the same
 * organisation would be accepted here.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    @ConditionalOnProperty(name = "freshchain.security.enabled", havingValue = "true", matchIfMissing = true)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/inventory/**").authenticated()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
                .build();
    }

    /** Test and local-demo escape hatch, off unless explicitly switched off. */
    @Bean
    @ConditionalOnProperty(name = "freshchain.security.enabled", havingValue = "false")
    public SecurityFilterChain permitAllFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter(SecurityProperties properties) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new JwtRoleConverter(properties.roleClaims()));
        return converter;
    }
}
