package com.hope.trading.news.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NewsServiceJwtServiceTest {
    @Test
    void validatesOnlyConfiguredCallersAndAudience() {
        SecretKey key = Keys.secretKeyFor(io.jsonwebtoken.SignatureAlgorithm.HS256);
        String encoded = Encoders.BASE64.encode(key.getEncoded());
        NewsServiceJwtProperties properties = new NewsServiceJwtProperties();
        properties.setEnabled(true);
        properties.setAudience("news-service");
        properties.setTrusted(Map.of("market-intelligence", encoded));
        properties.setAuthorizedCallers(Set.of("market-intelligence"));
        NewsServiceJwtService service = new NewsServiceJwtService(properties);
        Instant now = Instant.now();
        String token = Jwts.builder().issuer("market-intelligence")
                .subject("market-intelligence").audience().add("news-service").and()
                .claim("type", "service").issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(60))).signWith(key).compact();

        assertThat(service.validate(token).serviceName()).isEqualTo("market-intelligence");
    }
}
