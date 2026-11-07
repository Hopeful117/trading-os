package com.hope.trading.trading_core.security;

import com.hope.trading.trading_core.agent.application.AgentCredentialGenerator;
import com.hope.trading.trading_core.agent.model.AgentCredential;
import com.hope.trading.trading_core.agent.model.AgentIdentity;
import com.hope.trading.trading_core.agent.model.AgentType;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentJwtServiceTest {
    private static final String SECRET = "Y2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2Nj";
    private final Instant now = Instant.parse("2026-11-07T12:00:00Z");
    private final AgentJwtProperties properties = properties();
    private final AgentJwtService service = new AgentJwtService(properties,
            Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void issuesDistinctShortLivedAgentTokenWithTrustedClaims() {
        AgentIdentity agent = AgentIdentity.register(UUID.randomUUID(), "OpenCode", AgentType.OPENCODE, now);
        AgentCredential credential = AgentCredential.issue(agent.id(), "a".repeat(64), "a".repeat(16),
                2, now, null);

        AgentJwtService.IssuedToken issued = service.issue(agent, credential);

        AgentPrincipal principal = service.validate(issued.value());
        assertThat(principal.agentId()).isEqualTo(agent.id());
        assertThat(principal.ownerUserId()).isEqualTo(agent.ownerUserId());
        assertThat(principal.credentialId()).isEqualTo(credential.id());
        assertThat(principal.credentialVersion()).isEqualTo(2);
        assertThat(issued.expiresAt()).isEqualTo(now.plusSeconds(300));
    }

    @Test
    void rejectsWrongAudienceIssuerAndSignature() {
        AgentIdentity agent = AgentIdentity.register(UUID.randomUUID(), "OpenCode", AgentType.OPENCODE, now);
        AgentCredential credential = AgentCredential.issue(agent.id(), "a".repeat(64), "a".repeat(16),
                1, now, null);
        String token = service.issue(agent, credential).value();
        String wrongAudience = Jwts.builder().issuer(properties.getIssuer()).subject(agent.id().toString())
                .audience().add("wrong").and().claim("type", "agent")
                .claim("agent_id", agent.id().toString()).claim("owner_id", agent.ownerUserId().toString())
                .claim("credential_id", credential.id().toString()).claim("credential_version", 1)
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(300))).id(UUID.randomUUID().toString())
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET))).compact();

        assertThat(service.validate(token).agentId()).isEqualTo(agent.id());
        assertThatThrownBy(() -> service.validate(wrongAudience)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void rejectsHumanStyleTokenWithoutAgentClaims() {
        String humanToken = Jwts.builder().issuer("trading-os-test").subject(UUID.randomUUID().toString())
                .claim("role", "ROLE_USER").issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET))).compact();

        assertThatThrownBy(() -> service.validate(humanToken)).isInstanceOf(RuntimeException.class);
    }

    private AgentJwtProperties properties() {
        AgentJwtProperties value = new AgentJwtProperties();
        value.setSecret(SECRET);
        value.setIssuer("trading-os-agent-test");
        value.setAudience("trading-os-gateway-test");
        value.setExpirationSeconds(300);
        return value;
    }
}
