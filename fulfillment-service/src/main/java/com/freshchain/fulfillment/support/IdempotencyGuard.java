package com.freshchain.fulfillment.support;

import com.freshchain.fulfillment.repository.ProcessedEventRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * At-least-once delivery means every consumer will eventually see the same
 * event twice. This turns that into a no-op.
 *
 * <p>The claim must run inside the handler's transaction, so that the marker and
 * the state change commit together. A crash between them rolls back both, and
 * the redelivery is handled from a clean slate.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyGuard {

    private final ProcessedEventRepository processedEvents;

    /**
     * @return true if this consumer group has not seen the event before and
     *         should go ahead; false if it is a replay and should be dropped
     */
    public boolean claim(UUID eventId, String consumerGroup) {
        boolean claimed = processedEvents.tryClaim(eventId, consumerGroup) == 1;
        if (!claimed) {
            log.debug("replay of event {} ignored by group {}", eventId, consumerGroup);
        }
        return claimed;
    }
}
