package com.freshchain.order.config;

import com.freshchain.events.Topics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Topics themselves are declared once, by the inventory service. Declaring them
 * from every service would work — topic creation is idempotent — but it makes it
 * unclear who owns the partition count.
 */
@Configuration
public class KafkaConfig {

    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        DefaultErrorHandler handler = new DefaultErrorHandler(deadLetterRecoverer(template),
                new FixedBackOff(1_000L, 3L));
        handler.addNotRetryableExceptions(
                com.fasterxml.jackson.core.JsonProcessingException.class,
                IllegalArgumentException.class);
        return handler;
    }

    /**
     * Spring Kafka's default would name these {@code <topic>-dlt}. The naming is
     * pinned to {@code <topic>.DLT} here so the convention is ours and stays
     * stable across framework upgrades.
     *
     * <p>Partition is left as -1 so the producer picks by key rather than
     * mirroring the source partition, which would fail if a dead-letter topic
     * were ever created with a smaller partition count than its source.
     */
    private static DeadLetterPublishingRecoverer deadLetterRecoverer(KafkaTemplate<String, String> template) {
        return new DeadLetterPublishingRecoverer(template,
                (record, exception) -> new org.apache.kafka.common.TopicPartition(
                        Topics.deadLetterFor(record.topic()), -1));
    }
}
