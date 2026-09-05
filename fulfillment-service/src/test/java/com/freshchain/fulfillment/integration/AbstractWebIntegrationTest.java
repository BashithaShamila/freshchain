package com.freshchain.fulfillment.integration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base for API tests. Security is switched back <em>on</em> here — the point of
 * these tests is the authorisation rules, so running them against a permissive
 * filter chain would prove nothing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "freshchain.security.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(StubJwtDecoderConfig.class)
public abstract class AbstractWebIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

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
