package com.freshchain.inventory.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Reservation lifecycle tuning. */
@ConfigurationProperties(prefix = "freshchain.reservation")
public record ReservationProperties(Duration ttl, long sweepIntervalMs, int sweepBatchSize) {

    public ReservationProperties {
        ttl = ttl == null ? Duration.ofMinutes(15) : ttl;
        sweepBatchSize = sweepBatchSize <= 0 ? 200 : sweepBatchSize;
    }
}
