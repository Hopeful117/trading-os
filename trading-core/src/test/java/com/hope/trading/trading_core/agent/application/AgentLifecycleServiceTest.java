package com.hope.trading.trading_core.agent.application;

import com.hope.trading.trading_core.agent.api.AgentRequest;
import com.hope.trading.trading_core.agent.model.AgentCredential;
import com.hope.trading.trading_core.agent.model.AgentIdentity;
import com.hope.trading.trading_core.agent.model.AgentType;
import com.hope.trading.trading_core.agent.infrastructure.AgentAuditEventRepository;
import com.hope.trading.trading_core.agent.infrastructure.AgentAuditEventEntity;
import com.hope.trading.trading_core.agent.repository.AgentCredentialRepository;
import com.hope.trading.trading_core.agent.repository.AgentIdentityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentLifecycleServiceTest {
    @Mock
    private AgentIdentityRepository agents;
    @Mock
    private AgentCredentialRepository credentials;
    @Mock
    private AgentAuditEventRepository auditEvents;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID foreignOwnerId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-07T12:00:00Z");
    private AgentLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new AgentLifecycleService(agents, credentials, auditEvents,
                new AgentCredentialGenerator(), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void registrationUsesTheAuthenticatedOwner() {
        when(agents.save(any(AgentIdentity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.register(ownerId, new AgentRequest("OpenCode", AgentType.OPENCODE), "corr-1");

        assertThat(response.ownerUserId()).isEqualTo(ownerId);
        verify(agents).save(any(AgentIdentity.class));
        verify(auditEvents).save(any());
    }

    @Test
    void missingCorrelationIdIsReplacedWithAStableAuditCorrelation() {
        when(agents.save(any(AgentIdentity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.register(ownerId, new AgentRequest("OpenCode", AgentType.OPENCODE), null);

        ArgumentCaptor<AgentAuditEventEntity> captor = ArgumentCaptor.forClass(AgentAuditEventEntity.class);
        verify(auditEvents).save(captor.capture());
        assertThat(captor.getValue().correlationId()).isNotBlank();
    }

    @Test
    void foreignOwnerCannotReadAgent() {
        UUID agentId = UUID.randomUUID();
        when(agents.findByIdAndOwnerUserId(agentId, foreignOwnerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(foreignOwnerId, agentId))
                .isInstanceOf(AgentNotFoundException.class);
    }

    @Test
    void credentialIsReturnedOnceAndOnlyDigestIsPersisted() {
        AgentIdentity agent = AgentIdentity.register(ownerId, "OpenCode", AgentType.OPENCODE, now);
        when(agents.findForUpdateByIdAndOwnerUserId(agent.id(), ownerId)).thenReturn(Optional.of(agent));
        when(credentials.findFirstByAgentIdOrderByCredentialVersionDesc(agent.id())).thenReturn(Optional.empty());
        when(credentials.save(any(AgentCredential.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.issueCredential(ownerId, agent.id(), "corr-2");

        ArgumentCaptor<AgentCredential> captor = ArgumentCaptor.forClass(AgentCredential.class);
        verify(credentials).save(captor.capture());
        assertThat(response.credential()).isNotBlank();
        assertThat(captor.getValue().credentialFingerprint()).isNotEqualTo(response.credential());
        assertThat(response.fingerprint()).isEqualTo(captor.getValue().credentialFingerprint());
        verify(auditEvents).save(any());
    }

    @Test
    void suspendedAgentCannotIssueCredential() {
        AgentIdentity agent = AgentIdentity.register(ownerId, "OpenCode", AgentType.OPENCODE, now);
        agent.suspend(now.plusSeconds(1));
        when(agents.findForUpdateByIdAndOwnerUserId(agent.id(), ownerId)).thenReturn(Optional.of(agent));

        assertThatThrownBy(() -> service.issueCredential(ownerId, agent.id(), "corr-3"))
                .isInstanceOf(AgentLifecycleException.class);
        verify(credentials, never()).save(any());
    }

    @Test
    void issuingAgainRevokesThePreviousCredentialAndAdvancesVersion() {
        AgentIdentity agent = AgentIdentity.register(ownerId, "OpenCode", AgentType.OPENCODE, now);
        AgentCredential previous = AgentCredential.issue(agent.id(), "a".repeat(64), "a".repeat(16), 1, now, null);
        when(agents.findForUpdateByIdAndOwnerUserId(agent.id(), ownerId)).thenReturn(Optional.of(agent));
        when(credentials.findFirstByAgentIdOrderByCredentialVersionDesc(agent.id())).thenReturn(Optional.of(previous));
        when(credentials.save(any(AgentCredential.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.issueCredential(ownerId, agent.id(), "corr-rotation");

        assertThat(response.credentialVersion()).isEqualTo(2);
        verify(credentials).revokeActiveByAgentId(agent.id());
        verify(credentials).save(any(AgentCredential.class));
    }

    @Test
    void auditFailurePropagatesAndPreventsSuccessfulMutationResult() {
        when(agents.save(any(AgentIdentity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.doThrow(new IllegalStateException("audit unavailable"))
                .when(auditEvents).save(any(AgentAuditEventEntity.class));

        assertThatThrownBy(() -> service.register(ownerId,
                new AgentRequest("OpenCode", AgentType.OPENCODE), "corr-audit"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("audit unavailable");
    }
}
