package com.freshchain.inventory.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freshchain.events.Actor;
import com.freshchain.events.EventEnvelope;
import com.freshchain.events.EventTypes;
import com.freshchain.events.Topics;
import com.freshchain.inventory.domain.InventoryLot;
import com.freshchain.inventory.messaging.OrderPlacedConsumer;
import com.freshchain.inventory.messaging.OutboxPublisher;
import com.freshchain.inventory.repository.InventoryLotRepository;
import com.freshchain.inventory.repository.ReservationRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The event backbone end to end: an outbox row becomes a Kafka record, a replay
 * changes nothing, and a payload nobody can parse ends up on the dead-letter
 * topic instead of blocking its partition forever.
 */
class OutboxPublishingIT extends AbstractIntegrationTest {

    private static final String SKU = "CHK-BRST-5LB";

    @Autowired
    private OrderPlacedConsumer consumer;

    @Autowired
    private OutboxPublisher publisher;

    @Autowired
    private InventoryLotRepository lots;

    @Autowired
    private ReservationRepository reservations;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("an allocation reaches Kafka with its envelope intact")
    void outboxRowBecomesAKafkaRecord() throws Exception {
        lots.saveAndFlush(InventoryLot.receive(SKU, TestFixtures.WAREHOUSE, 50, LocalDate.now().plusDays(7)));
        UUID orderId = UUID.randomUUID();

        consumer.onOrderPlaced(envelopeJson(orderId, EventTypes.ORDER_PLACED,
                TestFixtures.order(orderId, false, TestFixtures.line(SKU, 40))));

        // The row is only marked published once the broker has acknowledged it.
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(unpublishedCount()).isZero());

        // Match on this order's key: the topic is shared across the whole test
        // JVM and still holds records from earlier tests, so "the first record"
        // is not the same thing as "our record".
        ConsumerRecord<String, String> record = readOne(Topics.INVENTORY_RESERVED,
                candidate -> orderId.toString().equals(candidate.key()), Duration.ofSeconds(20));

        assertThat(record.key())
                .as("keyed on orderId, which is what gives the saga its ordering")
                .isEqualTo(orderId.toString());

        JsonNode envelope = objectMapper.readTree(record.value());
        assertThat(envelope.get("eventType").asText()).isEqualTo(EventTypes.INVENTORY_RESERVED);
        assertThat(envelope.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(envelope.get("eventId").asText()).isNotBlank();
        assertThat(envelope.get("actor").get("roles").get(0).asText()).isEqualTo("CUSTOMER");

        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("status").asText()).isEqualTo("FULL");
        assertThat(payload.get("lines").get(0).get("qtyAllocated").asInt()).isEqualTo(40);
        assertThat(payload.get("lines").get(0).get("lots")).hasSize(1);
    }

    @Test
    @DisplayName("redelivering the same event allocates stock once, not twice")
    void replayedEventIsIgnored() throws Exception {
        InventoryLot lot = lots.saveAndFlush(
                InventoryLot.receive(SKU, TestFixtures.WAREHOUSE, 50, LocalDate.now().plusDays(7)));
        UUID orderId = UUID.randomUUID();
        String message = envelopeJson(orderId, EventTypes.ORDER_PLACED,
                TestFixtures.order(orderId, false, TestFixtures.line(SKU, 30)));

        consumer.onOrderPlaced(message);
        consumer.onOrderPlaced(message);

        assertThat(reservations.count()).isEqualTo(1);
        assertThat(lots.findById(lot.getLotId()).orElseThrow().getQtyReserved())
                .as("a redelivery must not hold the stock a second time")
                .isEqualTo(30);
    }

    @Test
    @DisplayName("an unreadable payload is parked on the dead-letter topic")
    void unparseablePayloadGoesToTheDlt() throws Exception {
        publish(Topics.ORDERS_PLACED, UUID.randomUUID().toString(), "{\"this\":\"is not an envelope\"");

        ConsumerRecord<String, String> parked = readOne(Topics.deadLetterFor(Topics.ORDERS_PLACED),
                candidate -> candidate.value().contains("is not an envelope"), Duration.ofSeconds(45));

        assertThat(parked.value()).contains("is not an envelope");
        assertThat(parked.headers().lastHeader("kafka_dlt-exception-message"))
                .as("the failure reason travels with the record")
                .isNotNull();
    }

    @Test
    @DisplayName("pruning removes old published rows and nothing else")
    void pruningRemovesOldPublishedRowsAndNothingElse() {
        UUID marker = UUID.randomUUID();
        // Old and published: eligible. Fresh and published: inside the retention
        // window. Old but unpublished: publication is still owed, age is irrelevant.
        insertOutboxRow(marker, "OldPublished", "now() - interval '10 days'", "now() - interval '10 days'");
        insertOutboxRow(marker, "FreshPublished", "now()", "now()");
        insertOutboxRow(marker, "OldUnpublished", "now() - interval '10 days'", null);

        publisher.prunePublished();

        assertThat(eventTypesFor(marker))
                .containsExactlyInAnyOrder("FreshPublished", "OldUnpublished");
    }

    // ---------------------------------------------------------------- helpers --

    private void insertOutboxRow(UUID aggregateId, String eventType, String createdAt, String publishedAt) {
        jdbc.update("""
                INSERT INTO outbox (aggregate_type, aggregate_id, event_type, topic, payload, created_at, published_at)
                VALUES ('Test', ?, ?, 'test.prune', '{}'::jsonb, %s, %s)
                """.formatted(createdAt, publishedAt == null ? "NULL" : publishedAt),
                aggregateId, eventType);
    }

    private List<String> eventTypesFor(UUID aggregateId) {
        return jdbc.queryForList(
                "SELECT event_type FROM outbox WHERE aggregate_id = ?", String.class, aggregateId);
    }

    private Integer unpublishedCount() {
        return jdbc.queryForObject("SELECT count(*) FROM outbox WHERE published_at IS NULL", Integer.class);
    }

    private <T> String envelopeJson(UUID aggregateId, String eventType, T payload) throws Exception {
        return objectMapper.writeValueAsString(EventEnvelope.of(
                eventType, aggregateId, null,
                new Actor(UUID.randomUUID().toString(), List.of("CUSTOMER")), payload));
    }

    private void publish(String topic, String key, String value) throws Exception {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, Containers.KAFKA.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>(topic, key, value)).get();
        }
    }

    /**
     * Assigns partitions explicitly and seeks to the beginning, rather than
     * subscribing under a throwaway group. Subscription would make the test wait
     * on group coordination, and on a topic created moments earlier it can also
     * sit on cached "topic does not exist" metadata for minutes.
     */
    private ConsumerRecord<String, String> readOne(String topic,
                                                    java.util.function.Predicate<ConsumerRecord<String, String>> match,
                                                    Duration timeout) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, Containers.KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.METADATA_MAX_AGE_CONFIG, "1000");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        long deadline = System.nanoTime() + timeout.toNanos();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            List<TopicPartition> partitions = List.of();
            while (partitions.isEmpty() && System.nanoTime() < deadline) {
                List<PartitionInfo> info = consumer.partitionsFor(topic, Duration.ofSeconds(2));
                if (info != null && !info.isEmpty()) {
                    partitions = info.stream()
                            .map(p -> new TopicPartition(topic, p.partition()))
                            .toList();
                }
            }
            if (partitions.isEmpty()) {
                throw new AssertionError("topic " + topic + " never appeared within " + timeout);
            }
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);

            while (System.nanoTime() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    if (match.test(record)) {
                        return record;
                    }
                }
            }
            throw new AssertionError("no matching record arrived on " + topic + " within " + timeout);
        }
    }
}
