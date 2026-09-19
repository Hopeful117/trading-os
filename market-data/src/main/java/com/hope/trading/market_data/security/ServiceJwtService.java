package com.hope.trading.market_data.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;

@Service
public class ServiceJwtService {
    private static final String TOKEN_TYPE = "service";
    private final ServiceJwtProperties properties;

    public ServiceJwtService(ServiceJwtProperties properties) {
        this.properties = properties;
        properties.validate();
    }

    public ServicePrincipal validate(String token) {
        if (!properties.isEnabled() || token == null || token.isBlank()) {
            throw new IllegalArgumentException("Service authentication is unavailable");
        }
        for (var trusted : properties.getTrusted().entrySet()) {
            try {
                Claims claims = Jwts.parser()
                        .verifyWith(key(trusted.getValue()))
                        .requireIssuer(trusted.getKey())
                        .require("type", TOKEN_TYPE)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
                if (!claims.getAudience().contains(properties.getAudience())
                        || !trusted.getKey().equals(claims.getSubject())) {
                    continue;
                }
                String actor = claims.get("actor_id", String.class);
                return new ServicePrincipal(trusted.getKey(), properties.getAudience(),
                        actor == null ? null : java.util.UUID.fromString(actor));
            } catch (RuntimeException ignored) {
                // Try the remaining configured caller keys before rejecting the credential.
            }
        }
        throw new IllegalArgumentException("Invalid service credential");
    }

    private SecretKey key(String value) {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(value));
    }
}
