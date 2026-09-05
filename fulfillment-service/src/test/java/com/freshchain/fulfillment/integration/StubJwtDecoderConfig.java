package com.freshchain.fulfillment.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * The resource server needs a decoder bean to wire its filter chain, but these
 * tests inject an already-authenticated token through MockMvc rather than
 * presenting a signed one.
 *
 * <p>This matters more with a hosted identity provider than it would with a
 * local one: reaching Asgardeo from the test suite would make the build depend
 * on an internet connection, a live tenant and somebody's password, and it would
 * be testing Asgardeo rather than these authorisation rules. The rules are what
 * is under test, so the token is injected already validated.
 */
@TestConfiguration
public class StubJwtDecoderConfig {

    // Deliberately not named `jwtDecoder`: the production JwtDecoderConfig
    // registers a bean by that name, and Spring Boot refuses to override a bean
    // definition. A distinct name plus @Primary lets both exist while this one
    // is the one injected.
    @Bean
    @Primary
    public JwtDecoder stubJwtDecoder() {
        return token -> {
            throw new UnsupportedOperationException("tests inject authentication directly");
        };
    }
}
