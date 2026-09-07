package com.freshchain.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

/**
 * A Redis token bucket keyed on the JWT subject, so one noisy customer cannot
 * spend everybody else's budget. Keying on IP would lump every customer behind
 * one corporate NAT into a single bucket.
 */
@Configuration
public class RateLimitConfig {

    @Bean
    @Primary
    public RedisRateLimiter redisRateLimiter(
            @Value("${freshchain.rate-limit.replenish-rate:20}") int replenishRate,
            @Value("${freshchain.rate-limit.burst-capacity:40}") int burstCapacity,
            @Value("${freshchain.rate-limit.requested-tokens:1}") int requestedTokens) {
        return new RedisRateLimiter(replenishRate, burstCapacity, requestedTokens);
    }

    @Bean
    public KeyResolver jwtSubjectKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(principal -> principal instanceof JwtAuthenticationToken jwt
                        ? jwt.getToken().getSubject()
                        : principal.getName())
                // An unauthenticated request has no subject to bill; bucket those
                // together so an anonymous flood cannot exhaust anything else.
                .defaultIfEmpty("anonymous")
                .switchIfEmpty(Mono.just("anonymous"));
    }
}
