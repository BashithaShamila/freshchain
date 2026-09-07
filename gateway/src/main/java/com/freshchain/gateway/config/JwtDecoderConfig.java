package com.freshchain.gateway.config;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

/**
 * The reactive twin of the resource services' decoder.
 *
 * <p>Asgardeo stamps access tokens with {@code typ: at+jwt} per RFC 9068, which
 * Spring Security's default verifier rejects. Keys come from an explicit JWKS
 * URL so start-up does not depend on the identity provider being reachable —
 * see the servlet twin for the full reasoning. The edge has to accept the same
 * token shape the services behind it do, or every request would be turned away
 * here before it ever reached them.
 */
@Configuration
public class JwtDecoderConfig {

    private static final JOSEObjectType ACCESS_TOKEN_TYPE = new JOSEObjectType("at+jwt");

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.audiences:}") String audiences) {

        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwtProcessorCustomizer(processor -> processor.setJWSTypeVerifier(
                        new DefaultJOSEObjectTypeVerifier<>(
                                ACCESS_TOKEN_TYPE, JOSEObjectType.JWT, null)))
                .build();

        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator());
        validators.add(new JwtIssuerValidator(issuerUri));

        // The SPA and the command-line tooling are separate Asgardeo
        // applications with different client ids, so both audiences are valid.
        Set<String> accepted = Arrays.stream(audiences.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toSet());
        if (!accepted.isEmpty()) {
            validators.add(new JwtClaimValidator<List<String>>(
                    JwtClaimNames.AUD,
                    claim -> claim != null && claim.stream().anyMatch(accepted::contains)));
        }
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }
}
