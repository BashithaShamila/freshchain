package com.freshchain.order.api;

import com.freshchain.order.domain.Requester;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/** Builds a {@link Requester} from the authenticated principal. */
public final class Requesters {

    private static final String ROLE_PREFIX = "ROLE_";

    public static Requester from(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("authentication required");
        }
        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .collect(Collectors.toSet());
        return new Requester(customerId(authentication.getName()), roles);
    }

    /**
     * Turns the token's subject into the customer id an order is filed under.
     *
     * <p>Asgardeo normally issues a UUID as {@code sub}, and that is used as-is.
     * But the subject attribute is configurable per application: point it at
     * username or email and {@code sub} arrives as {@code alice} or
     * {@code alice@example.com} instead. Rejecting those would make the service
     * fail on a legitimate identity-provider setting, so a non-UUID subject is
     * hashed into a stable name-based UUID.
     *
     * <p>The mapping is deterministic, so the same subject always resolves to
     * the same customer, and ownership checks keep working. It is one-way, which
     * is a small privacy benefit: an email address never reaches the orders
     * table. The cost is that the stored id is not the provider's own user id,
     * so correlating back to Asgardeo means going through the subject again.
     */
    static UUID customerId(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new AccessDeniedException("token has no subject");
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException notAUuid) {
            return UUID.nameUUIDFromBytes(subject.getBytes(StandardCharsets.UTF_8));
        }
    }

    private Requesters() {
    }
}
