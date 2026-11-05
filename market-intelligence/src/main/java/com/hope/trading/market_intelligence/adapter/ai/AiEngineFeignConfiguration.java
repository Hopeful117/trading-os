package com.hope.trading.market_intelligence.adapter.ai;

import feign.Request;
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

@SuppressWarnings("java:S2143")
public class AiEngineFeignConfiguration {
    private final String secret;

    public AiEngineFeignConfiguration(
            @Value("${MARKET_INTELLIGENCE_AI_ENGINE_SERVICE_JWT_SECRET:}") String secret) {
        this.secret = secret;
    }

    @Bean
    RequestInterceptor aiEngineAuthorizationInterceptor() {
        return template -> template.header("X-Service-Authorization", "Bearer " + issueToken());
    }

    @Bean
    Request.Options aiEngineRequestOptions(
            @Value("${MARKET_INTELLIGENCE_AI_ENGINE_CONNECT_TIMEOUT_MS:2000}") int connectTimeoutMs,
            @Value("${MARKET_INTELLIGENCE_AI_ENGINE_READ_TIMEOUT_MS:20000}") int readTimeoutMs) {
        return new Request.Options(connectTimeoutMs, readTimeoutMs);
    }

    private String issueToken() {
        if (secret.isBlank()) {
            throw new IllegalStateException("AI Engine service JWT secret is required");
        }
        Instant now = Instant.now();
        return Jwts.builder().issuer("market-intelligence").subject("market-intelligence")
                .audience().add("ai-engine").and().claim("type", "service")
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(60)))
                .id(UUID.randomUUID().toString()).signWith(signingKey()).compact();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }
}
