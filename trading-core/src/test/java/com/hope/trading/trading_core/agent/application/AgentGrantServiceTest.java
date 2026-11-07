package com.hope.trading.trading_core.agent.application;

import com.hope.trading.trading_core.agent.api.AgentGrantRequest;
import com.hope.trading.trading_core.agent.model.AgentGrant;
import com.hope.trading.trading_core.agent.model.AgentPermission;
import com.hope.trading.trading_core.agent.model.AgentResourceType;
import com.hope.trading.trading_core.agent.model.AgentStatus;
import com.hope.trading.trading_core.agent.infrastructure.AgentAuthorizationAuditEventRepository;
import com.hope.trading.trading_core.agent.repository.AgentGrantRepository;
import com.hope.trading.trading_core.agent.repository.AgentIdentityRepository;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.market_data.apiClient.MarketDataClient;
import com.hope.trading.trading_core.agent.model.AgentIdentity;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentGrantServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");

    @Mock private AgentIdentityRepository agents;
    @Mock private AgentGrantRepository grants;
    @Mock private BrokerAccountRepository brokerAccounts;
    @Mock private MarketDataClient markets;
    @Mock private AgentAuthorizationAuditEventRepository auditEvents;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID agentId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private AgentGrantService service;
    private AgentIdentity agent;

    @BeforeEach
    void setUp() {
        service = new AgentGrantService(agents, grants, brokerAccounts, markets, auditEvents,
                Clock.fixed(NOW, ZoneOffset.UTC));
        agent = AgentIdentity.register(ownerId, "researcher", com.hope.trading.trading_core.agent.model.AgentType.OPENCODE, NOW);
    }

    @Test
    void rejectsAGrantForAnotherUsersAgent() {
        when(agents.findForUpdateByIdAndOwnerUserId(agentId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(ownerId, agentId,
                new AgentGrantRequest(AgentPermission.ACCOUNTS_READ, AgentResourceType.BROKER_ACCOUNT, accountId, null),
                null)).isInstanceOf(AgentNotFoundException.class);
        verify(grants, never()).save(any());
    }

    @Test
    void rejectsAnIncompatiblePermissionBeforeResolvingTheResource() {
        when(agents.findForUpdateByIdAndOwnerUserId(agentId, ownerId)).thenReturn(Optional.of(agent));

        assertThatThrownBy(() -> service.create(ownerId, agentId,
                new AgentGrantRequest(AgentPermission.ACCOUNTS_READ, AgentResourceType.MARKET, accountId, null),
                null)).isInstanceOf(AgentGrantException.class)
                .hasMessageContaining("not compatible");
        verify(markets, never()).findById(any());
    }

    @Test
    void createsAnOwnedBrokerAccountGrant() {
        when(agents.findForUpdateByIdAndOwnerUserId(agentId, ownerId)).thenReturn(Optional.of(agent));
        when(brokerAccounts.findByIdAndOwnerId(accountId, ownerId)).thenReturn(Optional.of(mock(
                com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount.class)));
        when(grants.findByAgentIdAndPermissionAndResourceTypeAndResourceIdAndStatus(
                any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        when(grants.saveAndFlush(any(AgentGrant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(ownerId, agentId,
                new AgentGrantRequest(AgentPermission.ACCOUNTS_READ, AgentResourceType.BROKER_ACCOUNT, accountId, null),
                "correlation");

        assertThat(response.agentId()).isEqualTo(agentId);
        assertThat(response.permission()).isEqualTo(AgentPermission.ACCOUNTS_READ);
        verify(auditEvents).save(any());
    }

    @Test
    void rejectsGrantsForInactiveAgents() {
        when(agents.findForUpdateByIdAndOwnerUserId(agentId, ownerId)).thenReturn(Optional.of(agent));
        agent.suspend(NOW);

        assertThat(agent.status()).isEqualTo(AgentStatus.SUSPENDED);
        assertThatThrownBy(() -> service.create(ownerId, agentId,
                new AgentGrantRequest(AgentPermission.ACCOUNTS_READ, AgentResourceType.BROKER_ACCOUNT, accountId, null),
                null)).isInstanceOf(AgentGrantException.class)
                .hasMessageContaining("active agents");
    }
}
