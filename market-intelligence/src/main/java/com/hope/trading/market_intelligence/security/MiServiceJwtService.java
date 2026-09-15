package com.hope.trading.market_intelligence.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Map;
import java.util.UUID;

@Service
public class MiServiceJwtService {
    private static final String TOKEN_TYPE = "service";
    private final MiServiceJwtProperties properties;

    public MiServiceJwtService(MiServiceJwtProperties properties) {
        this.properties = properties;
    }

    public MiServicePrincipal validate(String token) {
        if (!properties.enabled() || token == null || token.isBlank()) {
            throw new IllegalArgumentException("Service authentication is unavailable");
        }
        for (Map.Entry<String, String> trusted : properties.trusted().entrySet()) {
            try {
                Claims claims = Jwts.parser()
                        .verifyWith(key(trusted.getValue()))
                        .requireIssuer(trusted.getKey())
                        .require("type", TOKEN_TYPE)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
                if (!trusted.getKey().equals(claims.getSubject())
                        || !claims.getAudience().contains(properties.audience())) {
                    continue;
                }
                String actor = claims.get("actor_id", String.class);
                return new MiServicePrincipal(trusted.getKey(), properties.audience(),
                        actor == null ? null : UUID.fromString(actor));
            } catch (RuntimeException ignored) {
                // Try the next explicitly trusted caller key.
            }
        }
        throw new IllegalArgumentException("Invalid service credential");
    }

    private SecretKey key(String encoded) {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(encoded));
    }
}
