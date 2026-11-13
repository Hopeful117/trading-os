package com.hope.trading.trading_core.agent.api;

import com.hope.trading.trading_core.agent.application.AgentAuthorizationService;
import com.hope.trading.trading_core.agent.model.AgentPermission;
import com.hope.trading.trading_core.broker.apiClient.BrokerAccountSnapshotV2Client;
import com.hope.trading.trading_core.broker.dto.BrokerAccountSnapshotV2Dto;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountReadOnlyStatusService;
import com.hope.trading.trading_core.broker.service.BrokerSynchronizationService;
import com.hope.trading.trading_core.broker.service.LiveCfdReconciliationService;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.dashboard.integration.BrokerDashboardMapper;
import com.hope.trading.trading_core.dashboard.integration.BrokerAccountFact;
import com.hope.trading.trading_core.dashboard.service.PositionQueryService;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.security.AgentPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.mock.web.MockHttpServletRequest;
import feign.FeignException;
import feign.Request;
import feign.Response;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
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
    @Mock private BrokerSynchronizationService synchronization;

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

    @Test
    void mapsBrokerSnapshotFailureToSanitizedStructuredDiagnostic() {
        UUID owner = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        BrokerAccount brokerAccount = account(accountId);
        Account tradingAccount = mock(Account.class);
        when(authorization.authorize(agentId, AgentPermission.POSITIONS_READ,
                com.hope.trading.trading_core.agent.model.AgentResourceType.BROKER_ACCOUNT,
                accountId, "positions-correlation"))
                .thenReturn(new AgentAuthorizationService.Decision(true, null));
        when(brokerAccounts.findByIdAndOwnerId(accountId, owner)).thenReturn(Optional.of(brokerAccount));
        when(accounts.findByBrokerAccountId(accountId)).thenReturn(Optional.of(tradingAccount));
        when(tradingAccount.getUser()).thenReturn(mock(com.hope.trading.trading_core.model.User.class));
        when(tradingAccount.getUser().getUserId()).thenReturn(owner);
        when(brokerAccount.executionMode()).thenReturn(com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode.LIVE);
        when(broker.getAccountSnapshot(accountId)).thenThrow(new IllegalStateException("provider detail"));

        assertThatThrownBy(() -> controller().positions(accountId,
                authentication(agentId, owner), "positions-correlation"))
                .isInstanceOf(AgentBusinessDataUnavailableException.class);

        var request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-ID", "positions-correlation");
        var response = new AgentBusinessExceptionHandler().dataUnavailable(
                new AgentBusinessDataUnavailableException(), request);
        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().getProperties()).containsEntry("code", "UPSTREAM_UNAVAILABLE");
        assertThat(response.getBody().getProperties()).containsEntry("correlationId", "positions-correlation");
    }

    @Test
    void fallsBackToLatestSnapshotWhenProviderSnapshotIsDeferred() {
        UUID owner = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        BrokerAccount brokerAccount = account(accountId);
        Account tradingAccount = mock(Account.class);
        BrokerAccountSnapshotV2Dto latest = mock(BrokerAccountSnapshotV2Dto.class);
        BrokerAccountFact facts = mock(BrokerAccountFact.class);
        Request request = Request.create(Request.HttpMethod.GET, "/snapshot", Map.of(),
                new byte[0], StandardCharsets.UTF_8, null);
        FeignException deferred = FeignException.errorStatus("snapshot",
                Response.builder().request(request).status(409).reason("conflict")
                        .headers(Map.of())
                        .body("{\"code\":\"BROKER_SNAPSHOT_DEFERRED\"}", StandardCharsets.UTF_8)
                        .build());

        when(authorization.authorize(agentId, AgentPermission.POSITIONS_READ,
                com.hope.trading.trading_core.agent.model.AgentResourceType.BROKER_ACCOUNT,
                accountId, null)).thenReturn(new AgentAuthorizationService.Decision(true, null));
        when(brokerAccounts.findByIdAndOwnerId(accountId, owner)).thenReturn(Optional.of(brokerAccount));
        when(accounts.findByBrokerAccountId(accountId)).thenReturn(Optional.of(tradingAccount));
        when(tradingAccount.getUser()).thenReturn(mock(com.hope.trading.trading_core.model.User.class));
        when(tradingAccount.getUser().getUserId()).thenReturn(owner);
        when(tradingAccount.getBaseCurrency()).thenReturn("USD");
        when(brokerAccount.executionMode()).thenReturn(com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode.LIVE);
        when(brokerAccount.providerRegistrationReference()).thenReturn(UUID.randomUUID());
        when(broker.getAccountSnapshot(accountId)).thenThrow(deferred);
        when(broker.getLatestAccountSnapshot(accountId)).thenReturn(latest);
        when(brokerMapper.toFact(latest, brokerAccount.providerRegistrationReference().toString(), "USD"))
                .thenReturn(facts);
        when(facts.balances()).thenReturn(Map.of("USD", BigDecimal.TEN));
        when(facts.positions()).thenReturn(List.of());
        when(positions.findPositions(any(), any(), any(), any())).thenReturn(List.of());

        var result = controller().positions(accountId, authentication(agentId, owner), null);

        assertThat(result.getBody()).isEmpty();
        verify(broker).getLatestAccountSnapshot(accountId);
    }

    @Test
    void exposesReadOnlyReconciliationOnlyWithTheExactAccountGrant() {
        UUID owner = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        BrokerAccount account = account(accountId);
        var expected = new LiveCfdReconciliationService.Result(
                LiveCfdReconciliationService.Status.MATCHED, List.of(), List.of());
        when(brokerAccounts.findByIdAndOwnerId(accountId, owner)).thenReturn(Optional.of(account));
        when(authorization.authorize(agentId, AgentPermission.ACCOUNTS_READ,
                com.hope.trading.trading_core.agent.model.AgentResourceType.BROKER_ACCOUNT,
                accountId, "c2")).thenReturn(new AgentAuthorizationService.Decision(true, null));
        when(synchronization.reconcileLiveCfd(owner, accountId)).thenReturn(expected);

        var result = controllerWithSynchronization().reconciliation(
                accountId, authentication(agentId, owner), "c2");

        assertThat(result.getBody()).isSameAs(expected);
        verify(synchronization).reconcileLiveCfd(owner, accountId);
    }

    private AgentBusinessController controller() {
        return new AgentBusinessController(brokerAccounts, accounts, authorization, readOnlyStatus,
                positions, broker, brokerMapper);
    }

    private AgentBusinessController controllerWithSynchronization() {
        return new AgentBusinessController(brokerAccounts, accounts, authorization, readOnlyStatus,
                positions, broker, brokerMapper, null, null, null, synchronization);
    }

    private UsernamePasswordAuthenticationToken authentication(UUID agentId, UUID ownerId) {
        return new UsernamePasswordAuthenticationToken(
                new AgentPrincipal(agentId, ownerId, UUID.randomUUID(), 1, "token"), null);
    }

    private BrokerAccount account(UUID ignoredId) {
        return mock(BrokerAccount.class);
    }
}
