package com.freshchain.inventory.integration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One Postgres and one Kafka broker for the whole test JVM.
 *
 * <p>Real infrastructure, not fakes: the behaviour under test — {@code FOR UPDATE}
 * semantics, check constraints, partial indexes, consumer group offsets — is
 * exactly what a mock would have to pretend to have.
 *
 * <p>Held here rather than on a base class so that both the headless and the
 * MockMvc bases can share the same running containers instead of each starting
 * their own.
 */
public final class Containers {

    // pgvector rather than stock postgres: the substitution advisor needs the
    // extension, and testing against a different image than production runs on
    // is how surprises get shipped.
    public static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("freshchain_inventory")
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

    /**
     * Resets transactional state between tests. Product rows are left alone:
     * they are reference data seeded by a Flyway migration, not test fixtures.
     */
    public static void reset(org.springframework.jdbc.core.JdbcTemplate jdbc) {
        jdbc.execute("""
                TRUNCATE reservation_line, reservation, inventory_lot, outbox, processed_event
                RESTART IDENTITY CASCADE
                """);
    }

    private Containers() {
    }
}
