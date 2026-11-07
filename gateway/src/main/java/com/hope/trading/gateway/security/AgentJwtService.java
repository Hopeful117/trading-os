package com.hope.trading.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.UUID;

@Service
public class AgentJwtService {
    private static final String TOKEN_TYPE = "agent";
    private final AgentJwtProperties properties;

    public AgentJwtService(AgentJwtProperties properties) {
        this.properties = properties;
        properties.validate();
    }

    public AgentPrincipal validate(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key())
                .requireIssuer(properties.getIssuer())
                .requireAudience(properties.getAudience())
                .require("type", TOKEN_TYPE)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        UUID subject = uuid(claims.getSubject());
        UUID agentId = uuid(claims.get("agent_id", String.class));
        UUID ownerId = uuid(claims.get("owner_id", String.class));
        UUID credentialId = uuid(claims.get("credential_id", String.class));
        Integer version = claims.get("credential_version", Integer.class);
        if (!subject.equals(agentId) || version == null || version < 1
                || claims.getId() == null || claims.getId().isBlank()
                || claims.getIssuedAt() == null || claims.getExpiration() == null) {
            throw new IllegalArgumentException("Invalid agent token");
        }
        return new AgentPrincipal(agentId, ownerId, credentialId, version, claims.getId());
    }

    private UUID uuid(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Invalid agent token");
        }
        return UUID.fromString(value);
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.getSecret()));
    }
}
