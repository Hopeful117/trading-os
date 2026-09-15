package com.hope.trading.market_intelligence.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MiServiceJwtServiceTest {
    private static final String CORE_SECRET = Base64.getEncoder().encodeToString(
            "trading-core-service-secret-32-bytes".getBytes());
    private static final String OTHER_SECRET = Base64.getEncoder().encodeToString(
            "other-service-secret-32-bytes-long".getBytes());

    @Test
    void validatesCoreTokenForMiAndExtractsDelegatedActor() {
        UUID actor = UUID.randomUUID();
        MiServiceJwtService service = receiver();
        String token = token(CORE_SECRET, "trading-core", "market-intelligence", actor,
                Instant.now().plusSeconds(60));

        MiServicePrincipal principal = service.validate(token);

        assertThat(principal.serviceName()).isEqualTo("trading-core");
        assertThat(principal.audience()).isEqualTo("market-intelligence");
        assertThat(principal.delegatedActor()).isEqualTo(actor);
    }

    @Test
    void rejectsWrongAudience() {
        assertThatThrownBy(() -> receiver().validate(
                token(CORE_SECRET, "trading-core", "broker-service", null,
                        Instant.now().plusSeconds(60))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidSignature() {
        assertThatThrownBy(() -> receiver().validate(
                token(OTHER_SECRET, "trading-core", "market-intelligence", null,
                        Instant.now().plusSeconds(60))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsExpiredToken() {
        assertThatThrownBy(() -> receiver().validate(
                token(CORE_SECRET, "trading-core", "market-intelligence", null,
                        Instant.now().minusSeconds(1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsValidTokenFromUnauthorizedCaller() {
        String token = token(CORE_SECRET, "other-service", "market-intelligence", null,
                Instant.now().plusSeconds(60));

        assertThatThrownBy(() -> receiver().validate(token))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void servicePrincipalRequiresDelegationOnlyWhenOperationNeedsIt() {
        MiServicePrincipal service = new MiServicePrincipal("trading-core", "market-intelligence", null);
        assertThat(service.delegatedActor()).isNull();
        assertThatThrownBy(service::requireDelegatedActor)
                .isInstanceOf(RuntimeException.class);
    }

    private MiServiceJwtService receiver() {
        MiServiceJwtProperties properties = new MiServiceJwtProperties();
        properties.setEnabled(true);
        properties.setAudience("market-intelligence");
        properties.setTrusted(Map.of("trading-core", CORE_SECRET));
        properties.setAuthorizedCallers(java.util.Set.of("trading-core"));
        return new MiServiceJwtService(properties);
    }

    private static String token(String secret, String issuer, String audience, UUID actor,
                                Instant expiration) {
        var builder = Jwts.builder().issuer(issuer).subject(issuer)
                .audience().add(audience).and().claim("type", "service")
                .issuedAt(new Date()).expiration(Date.from(expiration))
                .id(UUID.randomUUID().toString());
        if (actor != null) builder.claim("actor_id", actor.toString());
        return builder.signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))).compact();
    }
}
