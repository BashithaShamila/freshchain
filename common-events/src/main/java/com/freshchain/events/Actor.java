package com.freshchain.events;

import java.util.List;

/**
 * A Kafka record has no security context. Identity is carried in the envelope
 * so a consumer can audit who caused a state change; the consumer trusts the
 * producing service rather than re-validating a token it cannot refresh.
 */
public record Actor(String userId, List<String> roles) {

    public static final Actor SYSTEM = new Actor("system", List.of("SYSTEM"));

    public Actor {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
