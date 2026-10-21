package com.hope.trading.broker_service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

class ServiceJwtServiceTest {
    private static final String SECRET = Base64.getEncoder().encodeToString(
            "01234567890123456789012345678901".getBytes());

    @Test
    void issuesAndValidatesServiceTokenWithActor() {
        var properties = properties();
        var service = new ServiceJwtService(properties);
        var actor = UUID.randomUUID();

        var principal = service.validate(service.issue("broker-service", actor), "broker-service");

        assertThat(principal.serviceName()).isEqualTo("trading-core");
        assertThat(principal.audience()).isEqualTo("broker-service");
        assertThat(principal.actorId()).isEqualTo(actor);
    }

    @Test
    void rejectsInvalidTokenAfterTryingTrustedKeys() {
        var service = new ServiceJwtService(properties());

        assertThatThrownBy(() -> service.validate("not-a-token", "broker-service"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid service credential");
    }

    @Test
    void acceptsMarketIntelligenceTokenContract() {
        var properties = properties();
        properties.setTrusted(Map.of("market-intelligence", SECRET));
        var service = new ServiceJwtService(properties);
        var now = new Date();
        var token = Jwts.builder()
                .issuer("market-intelligence")
                .subject("market-intelligence")
                .audience().add("broker-service").and()
                .claim("type", "service")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000))
                .id(UUID.randomUUID().toString())
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET)))
                .compact();

        var principal = service.validate(token, "broker-service");

        assertThat(principal.serviceName()).isEqualTo("market-intelligence");
        assertThat(principal.audience()).isEqualTo("broker-service");
        assertThat(principal.actorId()).isNull();
    }

    private ServiceJwtProperties properties() {
        var properties = new ServiceJwtProperties();
        properties.setEnabled(true);
        properties.setName("trading-core");
        properties.setSecret(SECRET);
        properties.setTrusted(Map.of("trading-core", SECRET));
        return properties;
    }
}
