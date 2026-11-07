package com.hope.trading.trading_core.agent.api;

import com.hope.trading.trading_core.agent.application.AgentAuthorizationService;
import com.hope.trading.trading_core.agent.model.AgentPermission;
import com.hope.trading.trading_core.broker.apiClient.BrokerAccountSnapshotV2Client;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountReadOnlyStatusService;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.dashboard.integration.BrokerDashboardMapper;
import com.hope.trading.trading_core.dashboard.service.PositionQueryService;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.security.AgentPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentBusinessControllerTest {
    @Mock private BrokerAccountRepository brokerAccounts;
    @Mock private AccountRepository accounts;
    @Mock private AgentAuthorizationService authorization;
    @Mock private BrokerAccountReadOnlyStatusService readOnlyStatus;
    @Mock private PositionQueryService positions;
    @Mock private BrokerAccountSnapshotV2Client broker;
    @Mock private BrokerDashboardMapper brokerMapper;

    @Test
    void listsOnlyAccountsWithAnEffectiveGrant() {
        UUID owner = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        UUID allowedId = UUID.randomUUID();
        UUID deniedId = UUID.randomUUID();
        BrokerAccount allowed = account(allowedId);
        BrokerAccount denied = account(deniedId);
        when(allowed.id()).thenReturn(allowedId);
        when(denied.id()).thenReturn(deniedId);
        when(brokerAccounts.findAllByOwnerIdOrderByCreatedAtDesc(owner)).thenReturn(List.of(allowed, denied));
        when(authorization.authorize(eq(agentId), eq(AgentPermission.ACCOUNTS_READ), any(), any(), eq("c1")))
                .thenReturn(new AgentAuthorizationService.Decision(true, null))
                .thenReturn(new AgentAuthorizationService.Decision(false, "MISSING_OR_INEFFECTIVE_GRANT"));

        var result = controller().list(authentication(agentId, owner), "c1");

        assertThat(result.getBody()).hasSize(1);
        assertThat(result.getBody().getFirst().id()).isEqualTo(allowed.id());
    }

    @Test
    void deniesAccountDetailsWithoutTheExactGrant() {
        UUID owner = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        BrokerAccount account = account(accountId);
        when(brokerAccounts.findByIdAndOwnerId(accountId, owner)).thenReturn(Optional.of(account));
        when(authorization.authorize(agentId, AgentPermission.ACCOUNTS_READ,
                com.hope.trading.trading_core.agent.model.AgentResourceType.BROKER_ACCOUNT,
                accountId, null)).thenReturn(new AgentAuthorizationService.Decision(false, "MISSING_OR_INEFFECTIVE_GRANT"));

        assertThatThrownBy(() -> controller().get(accountId, authentication(agentId, owner), null))
                .isInstanceOf(AgentBusinessAuthorizationException.class);
        verifyNoInteractions(readOnlyStatus);
    }

    @Test
    void deniesPositionsBeforeLoadingAnotherOwnersData() {
        UUID owner = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        when(authorization.authorize(agentId, AgentPermission.POSITIONS_READ,
                com.hope.trading.trading_core.agent.model.AgentResourceType.BROKER_ACCOUNT,
                accountId, null)).thenReturn(new AgentAuthorizationService.Decision(false, "RESOURCE_NOT_OWNED_OR_UNKNOWN"));

        assertThatThrownBy(() -> controller().positions(accountId, authentication(agentId, owner), null))
                .isInstanceOf(AgentBusinessAuthorizationException.class);
        verifyNoInteractions(accounts, broker);
    }

    @Test
    void rejectsHumanPrincipalsOnTheAgentBoundary() {
        assertThatThrownBy(() -> controller().list(
                new UsernamePasswordAuthenticationToken("human", null), null))
                .isInstanceOf(AgentBusinessAuthorizationException.class);
        verifyNoInteractions(brokerAccounts, authorization);
    }

    private AgentBusinessController controller() {
        return new AgentBusinessController(brokerAccounts, accounts, authorization, readOnlyStatus,
                positions, broker, brokerMapper);
    }

    private UsernamePasswordAuthenticationToken authentication(UUID agentId, UUID ownerId) {
        return new UsernamePasswordAuthenticationToken(
                new AgentPrincipal(agentId, ownerId, UUID.randomUUID(), 1, "token"), null);
    }

    private BrokerAccount account(UUID ignoredId) {
        return mock(BrokerAccount.class);
    }
}
