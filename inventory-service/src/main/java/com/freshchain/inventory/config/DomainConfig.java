package com.freshchain.inventory.config;

import com.freshchain.inventory.domain.FefoAllocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfig {

    /**
     * The allocator is deliberately a plain object with no Spring annotations on
     * it, so it can be constructed and tested without a context. It is wired in
     * here rather than component-scanned to keep it that way.
     */
    @Bean
    public FefoAllocator fefoAllocator() {
        return new FefoAllocator();
    }
}
