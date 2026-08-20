package com.freshchain.inventory.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Covers the token shapes Asgardeo actually emits, plus the ones it emits once
 * somebody changes an attribute mapping in the console.
 */
class JwtRoleConverterTest {

    private final JwtRoleConverter converter = new JwtRoleConverter(List.of("roles", "groups"));

    @Test
    @DisplayName("application roles arrive as a flat array")
    void readsAFlatRolesArray() {
        assertThat(rolesFrom(jwt(Map.of("roles", List.of("CUSTOMER", "ADMIN")))))
                .containsExactlyInAnyOrder("ROLE_CUSTOMER", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("group names arrive path-qualified and only the last segment matters")
    void stripsGroupPathPrefixes() {
        assertThat(rolesFrom(jwt(Map.of("groups", List.of("Internal/admin", "/everyone")))))
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_EVERYONE");
    }

    @Test
    @DisplayName("a single role can arrive as a bare string rather than an array")
    void readsASingleStringClaim() {
        assertThat(rolesFrom(jwt(Map.of("roles", "CUSTOMER")))).containsExactly("ROLE_CUSTOMER");
    }

    @Test
    @DisplayName("several roles can arrive space- or comma-delimited in one string")
    void splitsDelimitedStrings() {
        assertThat(rolesFrom(jwt(Map.of("roles", "CUSTOMER, WAREHOUSE_OPERATOR ADMIN"))))
                .containsExactlyInAnyOrder("ROLE_CUSTOMER", "ROLE_WAREHOUSE_OPERATOR", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("hyphens and case are normalised so @PreAuthorize stays readable")
    void normalisesSeparatorsAndCase() {
        assertThat(rolesFrom(jwt(Map.of("roles", List.of("warehouse-operator", "Customer")))))
                .containsExactlyInAnyOrder("ROLE_WAREHOUSE_OPERATOR", "ROLE_CUSTOMER");
    }

    @Test
    @DisplayName("roles and groups are merged, not one-or-the-other")
    void mergesEveryConfiguredClaim() {
        assertThat(rolesFrom(jwt(Map.of("roles", List.of("CUSTOMER"), "groups", List.of("Internal/admin")))))
                .containsExactlyInAnyOrder("ROLE_CUSTOMER", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("the same role in two claims yields one authority")
    void deduplicates() {
        assertThat(rolesFrom(jwt(Map.of("roles", List.of("ADMIN"), "groups", List.of("admin")))))
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("a token with no role claim grants nothing")
    void grantsNothingWhenTheClaimIsAbsent() {
        // The failure mode to avoid is a missing claim being read as "allow".
        assertThat(rolesFrom(jwt(Map.of("scope", "openid")))).isEmpty();
    }

    @Test
    @DisplayName("only the configured claims are read")
    void ignoresUnconfiguredClaims() {
        JwtRoleConverter rolesOnly = new JwtRoleConverter(List.of("roles"));
        Jwt token = jwt(Map.of("roles", List.of("CUSTOMER"), "groups", List.of("ADMIN")));

        assertThat(rolesOnly.convert(token))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_CUSTOMER");
    }

    private List<String> rolesFrom(Jwt token) {
        return converter.convert(token).stream().map(GrantedAuthority::getAuthority).toList();
    }

    private static Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("6f9619ff-8b86-d011-b42d-00cf4fc964ff")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        claims.forEach(builder::claim);
        return builder.build();
    }
}
