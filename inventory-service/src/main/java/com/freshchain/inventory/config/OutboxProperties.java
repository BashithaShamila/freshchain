package com.freshchain.inventory.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "freshchain.outbox")
public record OutboxProperties(long pollIntervalMs, int batchSize,
                               Duration retention, long pruneIntervalMs) {

    public OutboxProperties {
        batchSize = batchSize <= 0 ? 100 : batchSize;
        retention = retention == null ? Duration.ofDays(3) : retention;
    }
}
