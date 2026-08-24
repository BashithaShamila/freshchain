package com.freshchain.order.domain;

import java.util.Set;
import java.util.UUID;

/**
 * Who is asking. Carried explicitly into the service layer rather than read from
 * a thread-local, so ownership rules are visible in the method signature and can
 * be tested without a security context.
 */
public record Requester(UUID userId, Set<String> roles) {

    public static final String ADMIN = "ADMIN";

    public Requester {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public boolean isAdmin() {
        return roles.contains(ADMIN);
    }
}
