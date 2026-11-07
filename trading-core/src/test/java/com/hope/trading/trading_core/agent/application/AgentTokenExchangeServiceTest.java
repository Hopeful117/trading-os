package com.hope.trading.trading_core.agent.application;

import com.hope.trading.trading_core.agent.api.AgentTokenAuthenticationException;
import com.hope.trading.trading_core.agent.api.AgentTokenRequest;
import com.hope.trading.trading_core.agent.infrastructure.AgentAuthenticationAuditEventRepository;
import com.hope.trading.trading_core.agent.model.AgentCredential;
import com.hope.trading.trading_core.agent.model.AgentIdentity;
import com.hope.trading.trading_core.agent.model.AgentStatus;
import com.hope.trading.trading_core.agent.model.AgentType;
import com.hope.trading.trading_core.agent.repository.AgentCredentialRepository;
import com.hope.trading.trading_core.agent.repository.AgentIdentityRepository;
import com.hope.trading.trading_core.security.AgentJwtProperties;
import com.hope.trading.trading_core.security.AgentJwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentTokenExchangeServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");
    private static final String SECRET = "Y2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2Nj";
    @Mock
    private AgentCredentialRepository credentials;
    @Mock
    private AgentIdentityRepository agents;
    @Mock
    private AgentAuthenticationAuditEventRepository audit;
    private AgentCredentialGenerator generator;
    private AgentTokenExchangeService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        generator = new AgentCredentialGenerator();
        AgentJwtProperties properties = new AgentJwtProperties();
        properties.setSecret(SECRET);
        properties.setIssuer("trading-os-agent-test");
        properties.setAudience("trading-os-gateway-test");
        properties.setExpirationSeconds(300);
        service = new AgentTokenExchangeService(credentials, agents, audit, generator,
                new AgentJwtService(properties, clock), new AgentExchangeRateLimiter(clock), clock);
    }

    @Test
    void exchangesOnlyAnActiveCredentialAndAuditsTheSuccess() {
        UUID owner = UUID.randomUUID();
        AgentIdentity agent = AgentIdentity.register(owner, "OpenCode", AgentType.OPENCODE, NOW);
        AgentCredentialGenerator.Material material = generator.generate();
        AgentCredential credential = AgentCredential.issue(agent.id(), material.digest(), material.fingerprint(),
                1, NOW, null);
        when(credentials.findByCredentialDigest(material.digest())).thenReturn(Optional.of(credential));
        when(agents.findById(agent.id())).thenReturn(Optional.of(agent));

        var response = service.exchange(new AgentTokenRequest(material.raw()), "127.0.0.1", "corr");

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        verify(audit).save(any());
    }

    @Test
    void rejectsUnknownAndRevokedCredentialsWithoutEnumeration() {
        when(credentials.findByCredentialDigest(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exchange(new AgentTokenRequest("wrong"), "127.0.0.1", "corr"))
                .isInstanceOf(AgentTokenAuthenticationException.class)
                .hasMessage("Agent authentication failed");

        UUID owner = UUID.randomUUID();
        AgentIdentity agent = AgentIdentity.register(owner, "OpenCode", AgentType.OPENCODE, NOW);
        AgentCredentialGenerator.Material revokedMaterial = generator.generate();
        AgentCredential credential = AgentCredential.issue(agent.id(), revokedMaterial.digest(),
                revokedMaterial.fingerprint(), 1, NOW, null);
        credential.revoke(NOW);
        when(credentials.findByCredentialDigest(revokedMaterial.digest())).thenReturn(Optional.of(credential));
        when(agents.findById(agent.id())).thenReturn(Optional.of(agent));

        assertThatThrownBy(() -> service.exchange(new AgentTokenRequest(revokedMaterial.raw()), "127.0.0.1", "corr"))
                .isInstanceOf(AgentTokenAuthenticationException.class);
    }

    @Test
    void suspendedAgentCannotExchangeEvenWithAnActiveCredential() {
        UUID owner = UUID.randomUUID();
        AgentIdentity agent = AgentIdentity.register(owner, "OpenCode", AgentType.OPENCODE, NOW);
        agent.suspend(NOW.plusSeconds(1));
        AgentCredentialGenerator.Material material = generator.generate();
        AgentCredential credential = AgentCredential.issue(agent.id(), material.digest(), material.fingerprint(),
                1, NOW, null);
        when(credentials.findByCredentialDigest(material.digest())).thenReturn(Optional.of(credential));
        when(agents.findById(agent.id())).thenReturn(Optional.of(agent));

        assertThatThrownBy(() -> service.exchange(new AgentTokenRequest(material.raw()), "127.0.0.1", null))
                .isInstanceOf(AgentTokenAuthenticationException.class);
        assertThat(agent.status()).isEqualTo(AgentStatus.SUSPENDED);
    }
}
