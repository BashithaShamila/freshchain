package com.freshchain.inventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "freshchain.substitution")
public record SubstitutionProperties(boolean enabled, int topK, double minSimilarity) {

    public SubstitutionProperties {
        topK = topK <= 0 ? 5 : topK;
    }
}
