package com.hope.trading.trading_core.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
public class ServiceJwtService {
    private static final String TOKEN_TYPE = "service";
    private final ServiceJwtProperties properties;

    public ServiceJwtService(ServiceJwtProperties properties) {
        this.properties = properties;
        properties.validate();
    }

    public String issue(String audience, UUID actorId) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .issuer(properties.getName())
                .subject(properties.getName())
                .audience().add(audience).and()
                .claim("type", TOKEN_TYPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.getExpirationSeconds())))
                .id(UUID.randomUUID().toString());
        if (actorId != null) {
            builder.claim("actor_id", actorId.toString());
        }
        return builder.signWith(key(properties.getAudienceSecrets()
                .getOrDefault(audience, properties.getSecret()))).compact();
    }

    public ServicePrincipal validate(String token, String expectedAudience) {
        if (!properties.isEnabled() || token == null || token.isBlank()) {
            throw new IllegalArgumentException("Service authentication is unavailable");
        }
        for (Map.Entry<String, String> trusted : properties.getTrusted().entrySet()) {
            try {
                Claims claims = Jwts.parser()
                        .verifyWith(key(trusted.getValue()))
                        .requireIssuer(trusted.getKey())
                        .require("type", TOKEN_TYPE)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
                if (!trusted.getKey().equals(claims.getSubject())
                        || !claims.getAudience().contains(expectedAudience)) {
                    continue;
                }
                String actor = claims.get("actor_id", String.class);
                return new ServicePrincipal(trusted.getKey(), expectedAudience,
                        actor == null ? null : UUID.fromString(actor));
            } catch (RuntimeException ignored) {
                // A token is tested against the configured caller keys; failure is rejected below.
            }
        }
        throw new IllegalArgumentException("Invalid service credential");
    }

    private SecretKey key(String value) {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(value));
    }
}
