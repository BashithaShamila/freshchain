package com.freshchain.order.integration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base for headless integration tests.
 *
 * <p>Test settings live in {@code application-test.yml} as a profile overlay
 * rather than a second {@code application.yml}, which would shadow the real one
 * on the classpath and silently drop everything it configures.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetTransactionalState() {
        Containers.reset(jdbcTemplate);
    }

    @DynamicPropertySource
    static void wireContainers(DynamicPropertyRegistry registry) {
        Containers.register(registry);
    }
}
