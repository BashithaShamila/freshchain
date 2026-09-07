package com.freshchain.gateway.config;

import io.netty.resolver.DefaultAddressResolverGroup;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Makes the gateway re-resolve downstream hostnames instead of pinning the first
 * address it ever saw.
 *
 * <p>Reactor Netty ships its own DNS resolver, which caches entries for the TTL
 * the DNS server advertises — and Docker's embedded DNS advertises a long one.
 * A container that is recreated comes back on a new IP, so the gateway keeps
 * dialling an address nothing is listening on and every request fails with
 * "Connection refused" until the gateway itself is restarted.
 *
 * <p>That is a local annoyance and a production problem: on ECS or Kubernetes a
 * task's IP changes on every rolling deployment, so a gateway that caches
 * forever would break after each release and recover only by being restarted.
 *
 * <p>Switching to the JVM's own resolver means lookups honour
 * {@code networkaddress.cache.ttl}, which the container image sets to a few
 * seconds. The gateway then recovers on its own within one TTL.
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public HttpClientCustomizer jvmDnsResolverCustomizer() {
        return httpClient -> httpClient.resolver(DefaultAddressResolverGroup.INSTANCE);
    }
}
