package com.hope.trading.market_intelligence.adapter.news;

import feign.RequestInterceptor;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@SuppressWarnings("java:S2143") // JJWT 0.x requires java.util.Date at this adapter boundary.
public class NewsFeignConfiguration {
    private final String secret;

    public NewsFeignConfiguration(
            @Value("${MARKET_INTELLIGENCE_NEWS_SERVICE_JWT_SECRET:}") String secret) {
        this.secret = secret;
    }

    @Bean
    RequestInterceptor newsAuthorizationInterceptor() {
        return template -> template.header("X-Service-Authorization", "Bearer " + issueToken());
    }

    private String issueToken() {
        if (secret.isBlank()) {
            throw new IllegalStateException("A News Service JWT secret is required for internal News calls");
        }
        Instant now = Instant.now();
        return Jwts.builder().issuer("market-intelligence").subject("market-intelligence")
                .audience().add("news-service").and().claim("type", "service")
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(60)))
                .id(UUID.randomUUID().toString()).signWith(signingKey()).compact();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }
}
