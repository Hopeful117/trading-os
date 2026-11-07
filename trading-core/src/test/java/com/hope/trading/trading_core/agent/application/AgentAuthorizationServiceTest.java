package com.hope.trading.trading_core.agent.application;

import com.hope.trading.trading_core.agent.infrastructure.AgentAuthorizationAuditEventRepository;
import com.hope.trading.trading_core.agent.model.AgentGrant;
import com.hope.trading.trading_core.agent.model.AgentPermission;
import com.hope.trading.trading_core.agent.model.AgentResourceType;
import com.hope.trading.trading_core.agent.model.AgentType;
import com.hope.trading.trading_core.agent.repository.AgentGrantRepository;
import com.hope.trading.trading_core.agent.repository.AgentIdentityRepository;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.market_data.apiClient.MarketDataClient;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AgentAuthorizationServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");

    @Mock private AgentIdentityRepository agents;
    @Mock private AgentGrantRepository grants;
    @Mock private BrokerAccountRepository brokerAccounts;
    @Mock private MarketDataClient markets;
    @Mock private AgentAuthorizationAuditEventRepository auditEvents;

    private AgentAuthorizationService service;
    private final UUID agentId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AgentAuthorizationService(agents, grants, brokerAccounts, markets, auditEvents,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void deniesWhenNoGrantExists() {
        var agent = com.hope.trading.trading_core.agent.model.AgentIdentity.register(ownerId, "agent", AgentType.OPENCODE, NOW);
        when(agents.findById(agentId)).thenReturn(Optional.of(agent));
        when(brokerAccounts.findByIdAndOwnerId(accountId, ownerId)).thenReturn(Optional.of(
                mock(com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount.class)));
        when(grants.findEffective(agentId, AgentPermission.ACCOUNTS_READ, AgentResourceType.BROKER_ACCOUNT,
                accountId, NOW)).thenReturn(Optional.empty());

        var decision = service.authorize(agentId, AgentPermission.ACCOUNTS_READ,
                AgentResourceType.BROKER_ACCOUNT, accountId, "correlation");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.denialReason()).isEqualTo("MISSING_OR_INEFFECTIVE_GRANT");
    }

    @Test
    void allowsOnlyTheExactOwnedResourceWithAnEffectiveGrant() {
        var agent = com.hope.trading.trading_core.agent.model.AgentIdentity.register(ownerId, "agent", AgentType.OPENCODE, NOW);
        when(agents.findById(agentId)).thenReturn(Optional.of(agent));
        when(brokerAccounts.findByIdAndOwnerId(accountId, ownerId)).thenReturn(Optional.of(
                mock(com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount.class)));
        when(grants.findEffective(agentId, AgentPermission.ACCOUNTS_READ, AgentResourceType.BROKER_ACCOUNT,
                accountId, NOW)).thenReturn(Optional.of(mock(AgentGrant.class)));

        var decision = service.authorize(agentId, AgentPermission.ACCOUNTS_READ,
                AgentResourceType.BROKER_ACCOUNT, accountId, "correlation");

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.denialReason()).isNull();
    }

    @Test
    void deniesResearchScopeUntilAUserOwnedScopeContractExists() {
        var decision = service.authorize(agentId, AgentPermission.RESEARCH_RUN,
                AgentResourceType.RESEARCH_SCOPE, UUID.randomUUID(), "correlation");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.denialReason()).isEqualTo("UNSUPPORTED_RESOURCE_SCOPE");
    }

    @Test
    void auditsUnknownAgentDenials() {
        var decision = service.authorize(agentId, AgentPermission.ACCOUNTS_READ,
                AgentResourceType.BROKER_ACCOUNT, accountId, "correlation");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.denialReason()).isEqualTo("UNKNOWN_AGENT");
        verify(auditEvents).save(any());
    }

    @Test
    void failsClosedWhenDenialAuditCannotBePersisted() {
        when(auditEvents.save(any())).thenThrow(new RuntimeException("database unavailable"));

        assertThatThrownBy(() -> service.authorize(agentId, AgentPermission.RESEARCH_RUN,
                AgentResourceType.RESEARCH_SCOPE, UUID.randomUUID(), "correlation"))
                .isInstanceOf(AgentAuthorizationAuditException.class);
    }
}
