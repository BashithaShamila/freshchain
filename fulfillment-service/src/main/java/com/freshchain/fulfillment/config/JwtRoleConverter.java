package com.freshchain.fulfillment.config;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Maps roles out of an access token onto Spring Security authorities, so
 * {@code @PreAuthorize("hasRole('ADMIN')")} works as written.
 *
 * <p>Spring Boot can do this declaratively with
 * {@code spring.security.oauth2.resourceserver.jwt.authorities-claim-name}, and
 * if your identity provider emits one flat array of clean role names, prefer
 * that over this class. This exists because real tokens are messier than that:
 *
 * <ul>
 *   <li><b>The claim name varies.</b> Asgardeo emits application roles under
 *       {@code roles} and group memberships under {@code groups}, and which one
 *       carries your authorisation model depends on how the application was set
 *       up. Several candidate claims are read, in order.</li>
 *   <li><b>The shape varies.</b> A claim may be a JSON array, a single string,
 *       or a space- or comma-delimited string.</li>
 *   <li><b>The names vary.</b> Group names commonly arrive path-qualified —
 *       {@code Internal/admin}, {@code /everyone} — so only the last segment is
 *       meaningful, and case and separators are inconsistent.</li>
 * </ul>
 *
 * <p>Normalising here rather than in each {@code @PreAuthorize} means the
 * authorisation rules stay readable and stay identical regardless of which
 * provider issued the token.
 */
public class JwtRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String ROLE_PREFIX = "ROLE_";

    private final List<String> claimNames;

    public JwtRoleConverter(List<String> claimNames) {
        this.claimNames = claimNames == null || claimNames.isEmpty()
                ? List.of("roles", "groups")
                : List.copyOf(claimNames);
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        for (String claimName : claimNames) {
            for (String raw : readClaim(jwt.getClaim(claimName))) {
                String role = normalise(raw);
                if (!role.isEmpty()) {
                    authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + role));
                }
            }
        }
        return authorities;
    }

    /** Accepts an array, a bare string, a delimited string, or a nested {@code {roles: [...]}}. */
    private static List<String> readClaim(Object claim) {
        return switch (claim) {
            case null -> List.of();
            case Collection<?> values -> values.stream().map(String::valueOf).toList();
            case String value -> List.of(value.split("[,\\s]+"));
            case Map<?, ?> nested -> readClaim(nested.get("roles"));
            default -> List.of();
        };
    }

    /**
     * {@code Internal/admin} and {@code warehouse-operator} both have to end up
     * matching the role names the code asks for.
     */
    private static String normalise(String raw) {
        String value = raw == null ? "" : raw.trim();
        int lastSegment = value.lastIndexOf('/');
        if (lastSegment >= 0) {
            value = value.substring(lastSegment + 1);
        }
        return value.replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }
}
