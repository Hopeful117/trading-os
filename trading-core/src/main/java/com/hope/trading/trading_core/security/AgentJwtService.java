package com.hope.trading.trading_core.security;

import com.hope.trading.trading_core.agent.model.AgentCredential;
import com.hope.trading.trading_core.agent.model.AgentIdentity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class AgentJwtService {
    public static final String TOKEN_TYPE = "agent";
    private final AgentJwtProperties properties;
    private final Clock clock;

    public AgentJwtService(AgentJwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        properties.validate();
    }

    public IssuedToken issue(AgentIdentity agent, AgentCredential credential) {
        Instant now = clock.instant();
        Instant expiresAt = now.plusSeconds(properties.getExpirationSeconds());
        String tokenId = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(agent.id().toString())
                .audience().add(properties.getAudience()).and()
                .claim("type", TOKEN_TYPE)
                .claim("agent_id", agent.id().toString())
                .claim("owner_id", agent.ownerUserId().toString())
                .claim("credential_id", credential.id().toString())
                .claim("credential_version", credential.credentialVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .id(tokenId)
                .signWith(key())
                .compact();
        return new IssuedToken(token, expiresAt, properties.getExpirationSeconds());
    }

    public AgentPrincipal validate(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Invalid agent token");
        }
        Claims claims = Jwts.parser()
                .verifyWith(key())
                .clock(() -> Date.from(clock.instant()))
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
        String tokenId = claims.getId();
        if (!subject.equals(agentId) || version == null || version < 1 || tokenId == null || tokenId.isBlank()
                || claims.getIssuedAt() == null || claims.getExpiration() == null) {
            throw new IllegalArgumentException("Invalid agent token");
        }
        return new AgentPrincipal(agentId, ownerId, credentialId, version, tokenId);
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

    public record IssuedToken(String value, Instant expiresAt, long expiresInSeconds) {
    }
}
