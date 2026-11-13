package com.hope.trading.broker_service.ctrader.position;

import com.hope.trading.broker_service.broker.domain.model.BrokerModels;
import com.hope.trading.broker_service.connection.application.BrokerConnectionRepository;
import com.hope.trading.broker_service.connection.domain.BrokerConnection;
import com.hope.trading.broker_service.connection.domain.BrokerPermission;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import com.hope.trading.broker_service.ctrader.authentication.CTraderOpenApiClient;
import com.hope.trading.broker_service.ctrader.authentication.CTraderAuthenticationException;
import com.hope.trading.broker_service.ctrader.authentication.CTraderRequestSafetyGuard;
import com.hope.trading.broker_service.ctrader.authentication.CTraderToken;
import com.hope.trading.broker_service.ctrader.authentication.CTraderTokenCodec;
import com.hope.trading.broker_service.ctrader.config.CTraderProperties;
import com.hope.trading.broker_service.ctrader.synchronization.CTraderReadOnlyObservation;
import com.hope.trading.broker_service.secret.application.SecretReader;
import com.hope.trading.broker_service.secret.domain.CredentialReference;
import com.hope.trading.broker_service.secret.domain.PlainSecret;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CTraderPositionManagementCapabilityTest {
    private static final Instant NOW = Instant.parse("2026-10-27T04:00:00Z");
    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID CREDENTIAL = UUID.randomUUID();

    @Mock BrokerConnectionRepository connections;
    @Mock SecretReader secrets;
    @Mock CTraderOpenApiClient client;
    @Mock CTraderRequestSafetyGuard safety;

    @Test
    void resolvesExactPositionIntoProviderScopedMutationTarget() {
        givenConnectedConnection();
        givenObservation(position("42", "EURUSD", "2"));

        var result = capability().resolveTarget(new BrokerModels.ResolveTargetRequest(ACCOUNT, "42"));

        assertThat(result.resolvedMutationScope()).isEqualTo("CTRADER|1001|42|EURUSD|2|" + NOW);
        verify(client).readOnlyObservation(any(), eq("1001"), any(), any());
    }

    @Test
    void submitsRequestedPartialQuantityAndStableClientRequestId() {
        givenConnectedConnection();
        givenObservation(position("42", "EURUSD", "2"));
        when(client.closePosition(any(), any())).thenReturn(
                new CTraderOpenApiClient.CTraderClosePositionResponse("order-1", "corr-1"));

        var result = capability().executeClose(new BrokerModels.ExecuteCloseRequest(
                ACCOUNT, "CTRADER|1001|42|EURUSD|2|" + NOW, BigDecimal.ONE, "close-key"));

        assertInstanceOf(BrokerModels.CloseAcknowledged.class, result);
        var captor = ArgumentCaptor.forClass(CTraderOpenApiClient.CTraderClosePositionRequest.class);
        verify(client).closePosition(any(), captor.capture());
        assertThat(captor.getValue().positionId()).isEqualTo(42L);
        assertThat(captor.getValue().quantity()).isEqualByComparingTo("1");
        assertThat(captor.getValue().clientRequestId()).isEqualTo(
                UUID.nameUUIDFromBytes("close-key".getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());
    }

    @Test
    void reconcilesRemainingExposureAsPartialAndClosingDealAsAbsent() {
        givenConnectedConnection();
        givenObservation(new CTraderReadOnlyObservation("1001", "USD", BigDecimal.valueOf(50000),
                BigDecimal.valueOf(50000), BigDecimal.ZERO, BigDecimal.valueOf(50000),
                List.of(position("42", "EURUSD", "1")), List.of(),
                List.of(new CTraderReadOnlyObservation.Deal("deal-partial", "42", "EURUSD", BigDecimal.ONE,
                        "SELL", BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE,
                        NOW.plusSeconds(1), true)), NOW, NOW, NOW, true, List.of()));
        var partial = capability().reconcile(new BrokerModels.ReconcileCloseRequest(
                ACCOUNT, "CTRADER|1001|42|EURUSD|2|" + NOW, "close-key"));

        var remaining = assertInstanceOf(BrokerModels.ExposureConfirmedPartial.class, partial);
        assertThat(remaining.remainingQuantity()).isEqualByComparingTo("1");

        reset(client);
        givenObservation(new CTraderReadOnlyObservation("1001", "USD", BigDecimal.valueOf(50000),
                BigDecimal.valueOf(50000), BigDecimal.ZERO, BigDecimal.valueOf(50000), List.of(), List.of(),
                List.of(new CTraderReadOnlyObservation.Deal("deal-1", "42", "EURUSD", BigDecimal.valueOf(2),
                        "SELL", BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, NOW.plusSeconds(1), true)),
                NOW, NOW, NOW, true, List.of()));
        var absent = capability().reconcile(new BrokerModels.ReconcileCloseRequest(
                ACCOUNT, "CTRADER|1001|42|EURUSD|2|" + NOW, "close-key"));

        assertThat(absent).isInstanceOf(BrokerModels.ExposureConfirmedAbsent.class)
                .withFailMessage("reconciliation result: %s", absent);
    }

    @Test
    void rejectsPartialCloseThatIsNotStrictlyBelowExposure() {
        givenConnectedConnection();
        givenObservation(position("42", "EURUSD", "2"));
        var result = capability().executeClose(new BrokerModels.ExecuteCloseRequest(
                ACCOUNT, "CTRADER|1001|42|EURUSD|2|" + NOW, BigDecimal.valueOf(2), "close-key"));
        assertThat(result).isInstanceOf(BrokerModels.CloseRejected.class)
                .extracting("reasonCode").isEqualTo("CTRADER_PARTIAL_QUANTITY_MUST_BE_LESS_THAN_POSITION");
    }

    @Test
    void rejectsCloseQuantityAboveExposure() {
        givenConnectedConnection();
        givenObservation(position("42", "EURUSD", "2"));
        var result = capability().executeClose(new BrokerModels.ExecuteCloseRequest(
                ACCOUNT, "CTRADER|1001|42|EURUSD|2|" + NOW, BigDecimal.valueOf(3), "close-key"));
        assertThat(result).isInstanceOf(BrokerModels.CloseRejected.class)
                .extracting("reasonCode").isEqualTo("CTRADER_PARTIAL_QUANTITY_MUST_BE_LESS_THAN_POSITION");
    }

    @Test
    void reconciliationRejectsScopeForAnotherAccount() {
        BrokerConnection connection = BrokerConnection.create(ACCOUNT, OWNER, BrokerProviderId.CTRADER, NOW);
        connection.bindExternalAccount("1001", "demo", NOW);
        connection.connected(CREDENTIAL, Set.of(BrokerPermission.READ_ACCOUNT, BrokerPermission.TRADE), "1001", null, NOW);
        connection.confirmProviderAccountEnvironment(false, NOW);
        when(connections.findByBrokerAccountId(ACCOUNT)).thenReturn(Optional.of(connection));
        var result = capability().reconcile(new BrokerModels.ReconcileCloseRequest(
                ACCOUNT, "CTRADER|9999|42|EURUSD|2|" + NOW, "close-key"));
        assertThat(result).isInstanceOf(BrokerModels.Inconclusive.class)
                .extracting("reasonCode").isEqualTo("CTRADER_ACCOUNT_MISMATCH");
    }

    @Test
    void reconciliationReportsCommandNotExecutedWhenExposureIsUnchanged() {
        givenConnectedConnection();
        givenObservation(position("42", "EURUSD", "2"));
        var result = capability().reconcile(new BrokerModels.ReconcileCloseRequest(
                ACCOUNT, "CTRADER|1001|42|EURUSD|2|" + NOW, "close-key"));
        assertThat(result).isInstanceOf(BrokerModels.CommandConfirmedNotExecuted.class);
    }

    @Test
    void rejectsMutationWhenAccountScopeOrSafetyPolicyDoesNotMatch() {
        givenConnectedConnection();
        var mismatch = capability().executeClose(new BrokerModels.ExecuteCloseRequest(
                ACCOUNT, "CTRADER|other|42|EURUSD|2|" + NOW, BigDecimal.ONE, "close-key"));
        assertThat(mismatch).isInstanceOf(BrokerModels.CloseRejected.class)
                .extracting("reasonCode").isEqualTo("CTRADER_ACCOUNT_MISMATCH");

        doThrow(new CTraderAuthenticationException("execution is disabled")).when(safety).requireExecutionEnabled();
        var disabled = capability().executeClose(new BrokerModels.ExecuteCloseRequest(
                ACCOUNT, "CTRADER|1001|42|EURUSD|2|" + NOW, BigDecimal.ONE, "close-key"));
        assertThat(disabled).isInstanceOf(BrokerModels.CloseRejected.class)
                .extracting("reasonCode").isEqualTo("CTRADER_EXECUTION_IS_DISABLED");
    }

    @Test
    void classifiesProviderFailuresAndInvalidScopesSafely() {
        givenConnectedConnection();
        givenObservation(position("42", "EURUSD", "2"));
        when(client.closePosition(any(), any())).thenThrow(new CTraderAuthenticationException("transport timeout"));
        var unknown = capability().executeClose(new BrokerModels.ExecuteCloseRequest(
                ACCOUNT, "CTRADER|1001|42|EURUSD|2|" + NOW, BigDecimal.ONE, "close-key"));
        assertThat(unknown).isInstanceOf(BrokerModels.CloseUnknown.class)
                .extracting("reasonCode").isEqualTo("CTRADER_CLOSE_OUTCOME_UNKNOWN");

        doThrow(new CTraderAuthenticationException("invalid token")).when(client).closePosition(any(), any());
        var rejected = capability().executeClose(new BrokerModels.ExecuteCloseRequest(
                ACCOUNT, "CTRADER|1001|42|EURUSD|2|" + NOW, BigDecimal.ONE, "close-key"));
        assertThat(rejected).isInstanceOf(BrokerModels.CloseRejected.class)
                .extracting("reasonCode").isEqualTo("CTRADER_INVALID_TOKEN");
        var invalidScope = capability().executeClose(new BrokerModels.ExecuteCloseRequest(
                ACCOUNT, "invalid", BigDecimal.ONE, "close-key"));
        assertThat(invalidScope).isInstanceOf(BrokerModels.CloseRejected.class)
                .extracting("reasonCode").isEqualTo("CTRADER_INVALID_POSITION_SCOPE");
    }

    @Test
    void reportsUnconfirmedAndStaleReconciliationStates() {
        givenConnectedConnection();
        givenObservation(new CTraderReadOnlyObservation("1001", "USD", BigDecimal.valueOf(50000),
                BigDecimal.valueOf(50000), BigDecimal.ZERO, BigDecimal.valueOf(50000),
                List.of(position("42", "EURUSD", "1")), List.of(), List.of(), NOW, NOW, NOW, true, List.of()));
        var partial = capability().reconcile(new BrokerModels.ReconcileCloseRequest(
                ACCOUNT, "CTRADER|1001|42|EURUSD|2|" + NOW, "key"));
        assertThat(partial).isInstanceOf(BrokerModels.Inconclusive.class)
                .extracting("reasonCode").isEqualTo("CTRADER_PARTIAL_CLOSE_NOT_CONFIRMED");

        givenObservation(new CTraderReadOnlyObservation("1001", "USD", BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of(), List.of(),
                NOW, NOW, NOW.minusSeconds(31), true, List.of()));
        var stale = capability().reconcile(new BrokerModels.ReconcileCloseRequest(
                ACCOUNT, "CTRADER|1001|42|EURUSD|2|" + NOW, "key"));
        assertThat(stale).isInstanceOf(BrokerModels.Inconclusive.class)
                .extracting("reasonCode").isEqualTo("CTRADER_RECONCILIATION_UNAVAILABLE");
    }

    @Test
    void handlesMissingOrAmbiguousPositionsAndIncompleteSnapshots() {
        givenConnectedConnection();
        givenObservation(new CTraderReadOnlyObservation("1001", "USD", BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of(), List.of(),
                NOW, NOW, NOW, true, List.of()));
        assertThatThrownBy(() -> capability().resolveTarget(new BrokerModels.ResolveTargetRequest(ACCOUNT, "missing")))
                .hasMessage("CTRADER_POSITION_NOT_FOUND");

        givenObservation(new CTraderReadOnlyObservation("1001", "USD", BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(position("42", "EURUSD", "1"), position("42", "EURUSD", "2")),
                List.of(), List.of(), NOW, NOW, NOW, true, List.of()));
        assertThatThrownBy(() -> capability().resolveTarget(new BrokerModels.ResolveTargetRequest(ACCOUNT, "42")))
                .hasMessage("CTRADER_AMBIGUOUS_POSITION");

        givenObservation(new CTraderReadOnlyObservation("1001", "USD", BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of(), List.of(),
                NOW, NOW, NOW, false, List.of("incomplete")));
        var incomplete = capability().reconcile(new BrokerModels.ReconcileCloseRequest(
                ACCOUNT, "CTRADER|1001|42|EURUSD|2|" + NOW, "key"));
        assertThat(incomplete).isInstanceOf(BrokerModels.Inconclusive.class)
                .extracting("reasonCode").isEqualTo("CTRADER_RECONCILIATION_UNAVAILABLE");
    }

    @Test
    void closesFullExposureAndReportsAbsentExposureWithoutDeal() {
        givenConnectedConnection();
        givenObservation(position("42", "EURUSD", "2"));
        when(client.closePosition(any(), any())).thenReturn(
                new CTraderOpenApiClient.CTraderClosePositionResponse("order-full", "corr-full"));
        var closed = capability().executeClose(new BrokerModels.ExecuteCloseRequest(
                ACCOUNT, "CTRADER|1001|42|EURUSD|2|" + NOW, null, "full-close"));
        assertThat(closed).isInstanceOf(BrokerModels.CloseAcknowledged.class);

        reset(client);
        givenObservation(new CTraderReadOnlyObservation("1001", "USD", BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of(), List.of(),
                NOW, NOW, NOW, true, List.of()));
        var absent = capability().reconcile(new BrokerModels.ReconcileCloseRequest(
                ACCOUNT, "CTRADER|1001|42|EURUSD|2|" + NOW, "full-close"));
        assertThat(absent).isInstanceOf(BrokerModels.Inconclusive.class)
                .extracting("reasonCode").isEqualTo("CTRADER_CLOSE_DEAL_NOT_CONFIRMED");
    }

    @Test
    void admitsLiveFtmoFreeAccountForPositionManagement() {
        BrokerConnection connection = BrokerConnection.create(ACCOUNT, OWNER, BrokerProviderId.CTRADER, NOW);
        connection.bindExternalAccount("1001", "demo", NOW);
        connection.connected(CREDENTIAL, Set.of(BrokerPermission.READ_ACCOUNT, BrokerPermission.TRADE), "1001", null, NOW);
        connection.confirmProviderAccountEnvironment(true, NOW);
        when(connections.findByBrokerAccountId(ACCOUNT)).thenReturn(Optional.of(connection));
        lenient().when(secrets.read(any(CredentialReference.class))).thenAnswer(invocation -> new PlainSecret(
                CTraderTokenCodec.encode(new CTraderToken("access", "refresh", NOW.plusSeconds(3600)))));
        givenObservation(position("42", "EURUSD", "2"));

        var result = new CTraderPositionManagementCapability(connections, secrets, client,
                new CTraderProperties("client", "secret", "https://callback", "https://auth", "demo",
                        "FTMO_FREE", "https://token", "wss://demo.ctraderapi.com:5036", 600), safety,
                Clock.fixed(NOW, ZoneOffset.UTC)).resolveTarget(
                new BrokerModels.ResolveTargetRequest(ACCOUNT, "42"));

        assertThat(result.resolvedMutationScope()).startsWith("CTRADER|1001|42|");
    }

    @Test
    void rejectsExpiredPositionManagementToken() {
        givenConnectedConnection();
        when(secrets.read(any(CredentialReference.class))).thenReturn(new PlainSecret(
                CTraderTokenCodec.encode(new CTraderToken("access", "refresh", NOW.minusSeconds(1)))));

        assertThatThrownBy(() -> capability().resolveTarget(new BrokerModels.ResolveTargetRequest(ACCOUNT, "42")))
                .hasMessage("cTrader authentication has expired");
    }

    private void givenConnectedConnection() {
        BrokerConnection connection = BrokerConnection.create(ACCOUNT, OWNER, BrokerProviderId.CTRADER, NOW);
        connection.bindExternalAccount("1001", "demo", NOW);
        connection.connected(CREDENTIAL, Set.of(BrokerPermission.READ_ACCOUNT, BrokerPermission.TRADE), "1001", null, NOW);
        connection.confirmProviderAccountEnvironment(false, NOW);
        when(connections.findByBrokerAccountId(ACCOUNT)).thenReturn(Optional.of(connection));
        lenient().when(secrets.read(any(CredentialReference.class))).thenAnswer(invocation -> new PlainSecret(
                CTraderTokenCodec.encode(new CTraderToken("access", "refresh", NOW.plusSeconds(3600)))));
    }

    private void givenObservation(CTraderReadOnlyObservation.Position position) {
        givenObservation(new CTraderReadOnlyObservation("1001", "USD", BigDecimal.valueOf(50000),
                BigDecimal.valueOf(50000), BigDecimal.ZERO, BigDecimal.valueOf(50000), List.of(position),
                List.of(), List.of(), NOW, NOW, NOW, true, List.of()));
    }

    private void givenObservation(CTraderReadOnlyObservation observation) {
        when(client.readOnlyObservation(any(), eq("1001"), any(), any())).thenReturn(observation);
    }

    private CTraderReadOnlyObservation.Position position(String reference, String instrument, String quantity) {
        return new CTraderReadOnlyObservation.Position(instrument, new BigDecimal(quantity), BigDecimal.ONE,
                NOW, reference);
    }

    private CTraderPositionManagementCapability capability() {
        return new CTraderPositionManagementCapability(connections, secrets, client,
                new CTraderProperties("client", "secret", "https://callback", "https://auth", "demo",
                        "https://token", "wss://demo.ctraderapi.com:5036", 600), safety,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
