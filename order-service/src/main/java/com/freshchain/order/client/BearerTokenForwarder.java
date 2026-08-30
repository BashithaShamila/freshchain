package com.freshchain.order.client;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Forwards the caller's own token downstream rather than using a service
 * account, so the inventory service authorises the actual customer and the
 * audit trail stays intact across the hop.
 */
@Configuration
public class BearerTokenForwarder {

    @Bean
    public RequestInterceptor bearerTokenRequestInterceptor() {
        return template -> {
            if (SecurityContextHolder.getContext().getAuthentication()
                    instanceof JwtAuthenticationToken jwt) {
                template.header("Authorization", "Bearer " + jwt.getToken().getTokenValue());
            }
        };
    }
}
