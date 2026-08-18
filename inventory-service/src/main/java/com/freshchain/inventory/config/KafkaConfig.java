package com.freshchain.inventory.config;

import com.freshchain.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    private static final int PARTITIONS = 3;
    private static final short REPLICAS = 1;

    @Bean
    public NewTopic ordersPlacedTopic() {
        return topic(Topics.ORDERS_PLACED);
    }

    @Bean
    public NewTopic ordersConfirmedTopic() {
        return topic(Topics.ORDERS_CONFIRMED);
    }

    @Bean
    public NewTopic ordersCancelledTopic() {
        return topic(Topics.ORDERS_CANCELLED);
    }

    @Bean
    public NewTopic inventoryReservedTopic() {
        return topic(Topics.INVENTORY_RESERVED);
    }

    @Bean
    public NewTopic inventoryReleasedTopic() {
        return topic(Topics.INVENTORY_RELEASED);
    }

    @Bean
    public NewTopic fulfillmentShippedTopic() {
        return topic(Topics.FULFILLMENT_SHIPPED);
    }

    @Bean
    public NewTopic ordersPlacedDlt() {
        return topic(Topics.deadLetterFor(Topics.ORDERS_PLACED));
    }

    @Bean
    public NewTopic ordersConfirmedDlt() {
        return topic(Topics.deadLetterFor(Topics.ORDERS_CONFIRMED));
    }

    @Bean
    public NewTopic ordersCancelledDlt() {
        return topic(Topics.deadLetterFor(Topics.ORDERS_CANCELLED));
    }

    @Bean
    public NewTopic fulfillmentShippedDlt() {
        return topic(Topics.deadLetterFor(Topics.FULFILLMENT_SHIPPED));
    }

    @Bean
    public NewTopic inventoryReservedDlt() {
        return topic(Topics.deadLetterFor(Topics.INVENTORY_RESERVED));
    }

    @Bean
    public NewTopic inventoryReleasedDlt() {
        return topic(Topics.deadLetterFor(Topics.INVENTORY_RELEASED));
    }

    /**
     * Three quick retries, then the record is parked on {@code <topic>.DLT} with
     * the failure reason in its headers.
     *
     * <p>The retries exist for transient faults — a lock timeout, a broker blip.
     * A malformed payload will fail identically every time, so those exceptions
     * are marked non-retryable and go straight to the dead-letter topic instead
     * of blocking the partition behind a record that can never succeed.
     */
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

    private static NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(PARTITIONS).replicas(REPLICAS).build();
    }
}
