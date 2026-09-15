package com.hope.trading.broker_service.security;

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
    private final ServiceJwtProperties properties;

    public ServiceJwtService(ServiceJwtProperties properties) {
        this.properties = properties;
        properties.validate();
    }

    public String issue(String audience, UUID actorId) {
        Instant now = Instant.now();
        var builder = Jwts.builder().issuer(properties.name()).subject(properties.name())
                .audience().add(audience).and().claim("type", "service")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.expirationSeconds())))
                .id(UUID.randomUUID().toString());
        if (actorId != null) builder.claim("actor_id", actorId.toString());
        return builder.signWith(key(properties.secret())).compact();
    }

    public ServicePrincipal validate(String token, String audience) {
        if (!properties.enabled() || token == null || token.isBlank())
            throw new IllegalArgumentException("Service authentication is unavailable");
        for (Map.Entry<String, String> trusted : properties.trusted().entrySet()) {
            try {
                Claims claims = Jwts.parser().verifyWith(key(trusted.getValue()))
                        .requireIssuer(trusted.getKey()).require("type", "service").build()
                        .parseSignedClaims(token).getPayload();
                if (!trusted.getKey().equals(claims.getSubject())
                        || !claims.getAudience().contains(audience)) continue;
                String actor = claims.get("actor_id", String.class);
                return new ServicePrincipal(trusted.getKey(), audience,
                        actor == null ? null : UUID.fromString(actor));
            } catch (RuntimeException ignored) { }
        }
        throw new IllegalArgumentException("Invalid service credential");
    }

    private SecretKey key(String value) { return Keys.hmacShaKeyFor(Decoders.BASE64.decode(value)); }
}
