package com.freshchain.fulfillment.config;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Decodes Asgardeo access tokens.
 *
 * <p>Exists for one reason: Asgardeo stamps its access tokens with the JOSE
 * header {@code typ: at+jwt}, as specified by RFC 9068 (JWT Profile for OAuth
 * 2.0 Access Tokens). Spring Security's default decoder accepts only
 * {@code typ: JWT} or no {@code typ} at all, so an otherwise perfectly valid
 * token is rejected with "JOSE header typ (type) at+jwt not allowed".
 *
 * <p>That is a real interoperability gap rather than a misconfiguration: the
 * whole point of {@code at+jwt} is to let a resource server tell an access token
 * apart from an ID token, so accepting it is more correct than not. Both types
 * are permitted here — {@code at+jwt} for Asgardeo, plain {@code JWT} for
 * providers that predate the RFC.
 *
 * <p>Defining the decoder by hand means Boot's
 * {@code spring.security.oauth2.resourceserver.jwt.audiences} property no longer
 * wires itself up, so the validators are assembled explicitly below. Dropping
 * the audience check silently would have been the easy mistake here.
 *
 * <p>The signing keys are fetched from an explicit JWKS URL rather than
 * discovered from the issuer. {@code withIssuerLocation} would be tidier, but it
 * resolves the discovery document <em>eagerly, at start-up</em>: every service
 * would then refuse to start whenever Asgardeo is briefly unreachable, which is
 * a hard availability dependency on an external service for a system that
 * otherwise survives its broker disappearing. {@code withJwkSetUri} defers the
 * fetch to the first token, so a restart during an identity-provider blip comes
 * up healthy and serves as soon as keys are reachable.
 *
 * <p>The issuer is still validated — it is simply checked as a claim rather than
 * used to look up the keys.
 *
 * <p>Several audiences are accepted because one deployment legitimately serves
 * more than one client: the React single-page app is a public client with its
 * own Asgardeo application, while the command-line tooling is a confidential
 * one. They hold different client ids, so their tokens carry different
 * {@code aud} values, and both are valid here. The check still refuses a token
 * minted for an application outside this list.
 */
@Configuration
@ConditionalOnProperty(name = "freshchain.security.enabled", havingValue = "true", matchIfMissing = true)
public class JwtDecoderConfig {

    private static final JOSEObjectType ACCESS_TOKEN_TYPE = new JOSEObjectType("at+jwt");

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.audiences:}") String audiences) {

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwtProcessorCustomizer(processor -> processor.setJWSTypeVerifier(
                        new DefaultJOSEObjectTypeVerifier<>(
                                ACCESS_TOKEN_TYPE, JOSEObjectType.JWT, null)))
                .build();

        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator());
        validators.add(new JwtIssuerValidator(issuerUri));

        Set<String> accepted = Arrays.stream(audiences.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toSet());
        if (!accepted.isEmpty()) {
            // Without this, any token signed by the same Asgardeo organisation
            // would be accepted here, including one minted for another
            // application entirely.
            validators.add(new JwtClaimValidator<List<String>>(
                    JwtClaimNames.AUD,
                    claim -> claim != null && claim.stream().anyMatch(accepted::contains)));
        }
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }
}
