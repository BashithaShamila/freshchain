package com.freshchain.gateway.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param enabled    whether the resource server enforces anything; off only for tests
 * @param roleClaims access-token claims to read roles from, in order of preference
 */
@ConfigurationProperties(prefix = "freshchain.security")
public record SecurityProperties(boolean enabled, List<String> roleClaims) {

    public SecurityProperties {
        roleClaims = roleClaims == null || roleClaims.isEmpty() ? List.of("roles", "groups") : roleClaims;
    }
}
