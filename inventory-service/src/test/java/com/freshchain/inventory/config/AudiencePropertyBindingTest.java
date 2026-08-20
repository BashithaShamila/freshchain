package com.freshchain.inventory.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

/**
 * Pins the behaviour of an unset audience.
 *
 * <p>{@code application.yml} carries {@code audiences: ${FRESHCHAIN_OAUTH_AUDIENCE:}},
 * so a deployment that has not set a client id yet resolves it to an empty
 * string. Spring Boot adds an audience validator only when the bound list is
 * non-empty — but if an empty string instead bound to a list containing one
 * blank entry, every request would fail validation against an audience no token
 * can carry, and the whole system would 401 with nothing obviously wrong.
 *
 * <p>That failure mode is invisible until a token is presented, so it is worth
 * asserting rather than trusting.
 */
class AudiencePropertyBindingTest {

    @Test
    @DisplayName("an unset audience binds to no audience at all, not to one blank audience")
    void emptyAudienceBindsToAnEmptyList() {
        assertThat(bind("").getJwt().getAudiences()).isEmpty();
    }

    @Test
    @DisplayName("a configured audience binds through so the validator is applied")
    void configuredAudienceBinds() {
        assertThat(bind("my-client-id").getJwt().getAudiences()).containsExactly("my-client-id");
    }

    private static OAuth2ResourceServerProperties bind(String audiences) {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.security.oauth2.resourceserver.jwt.audiences", audiences);
        return Binder.get(environment)
                .bind("spring.security.oauth2.resourceserver",
                        org.springframework.boot.context.properties.bind.Bindable
                                .of(OAuth2ResourceServerProperties.class))
                .orElseGet(OAuth2ResourceServerProperties::new);
    }
}
