package com.freshchain.fulfillment.integration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One Postgres and one Kafka broker for the whole test JVM. Real infrastructure
 * rather than fakes, because the behaviour under test — transactional writes,
 * consumer group offsets, dead-letter routing — is exactly what a fake would
 * have to pretend to have.
 */
public final class Containers {

    public static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("freshchain_fulfillment")
            .withUsername("freshchain")
            .withPassword("freshchain");

    public static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"));

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    /** Resets transactional state between tests, leaving Flyway-seeded reference data alone. */
    public static void reset(org.springframework.jdbc.core.JdbcTemplate jdbc) {
        jdbc.execute("TRUNCATE shipment_line, shipment, order_allocation, outbox, processed_event RESTART IDENTITY CASCADE");
    }

    private Containers() {
    }
}
