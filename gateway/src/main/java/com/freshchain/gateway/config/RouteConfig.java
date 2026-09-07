package com.freshchain.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Routes are declared in Java rather than YAML on purpose: they are the edge's
 * behaviour, they get read far more often than they get edited, and a typo in a
 * predicate string fails at startup here instead of at the first request.
 */
@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder,
                               RateLimiter<?> rateLimiter,
                               KeyResolver jwtSubjectKeyResolver,
                               @Value("${freshchain.routes.order-service}") String orderService,
                               @Value("${freshchain.routes.inventory-service}") String inventoryService,
                               @Value("${freshchain.routes.fulfillment-service}") String fulfillmentService) {
        return builder.routes()
                .route("orders", r -> r.path("/api/v1/orders/**")
                        .filters(f -> f.requestRateLimiter(c -> c
                                .setRateLimiter(rateLimiter)
                                .setKeyResolver(jwtSubjectKeyResolver)))
                        .uri(orderService))
                .route("inventory", r -> r.path("/api/v1/inventory/**", "/api/v1/lots/**", "/api/v1/reservations/**")
                        .filters(f -> f.requestRateLimiter(c -> c
                                .setRateLimiter(rateLimiter)
                                .setKeyResolver(jwtSubjectKeyResolver)))
                        .uri(inventoryService))
                .route("shipments", r -> r.path("/api/v1/shipments/**")
                        .filters(f -> f.requestRateLimiter(c -> c
                                .setRateLimiter(rateLimiter)
                                .setKeyResolver(jwtSubjectKeyResolver)))
                        .uri(fulfillmentService))
                // Docs are proxied so the three services can be browsed from one
                // origin without CORS gymnastics.
                .route("order-docs", r -> r.path("/docs/orders/**")
                        .filters(f -> f.rewritePath("/docs/orders/(?<segment>.*)", "/${segment}"))
                        .uri(orderService))
                .route("inventory-docs", r -> r.path("/docs/inventory/**")
                        .filters(f -> f.rewritePath("/docs/inventory/(?<segment>.*)", "/${segment}"))
                        .uri(inventoryService))
                .route("fulfillment-docs", r -> r.path("/docs/fulfillment/**")
                        .filters(f -> f.rewritePath("/docs/fulfillment/(?<segment>.*)", "/${segment}"))
                        .uri(fulfillmentService))
                .build();
    }
}
