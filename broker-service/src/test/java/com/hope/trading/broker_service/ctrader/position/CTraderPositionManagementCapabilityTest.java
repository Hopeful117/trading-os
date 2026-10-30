package com.hope.trading.broker_service.ctrader.position;

import com.hope.trading.broker_service.broker.domain.model.BrokerModels;
import com.hope.trading.broker_service.connection.application.BrokerConnectionRepository;
import com.hope.trading.broker_service.connection.domain.BrokerConnection;
import com.hope.trading.broker_service.connection.domain.BrokerPermission;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import com.hope.trading.broker_service.ctrader.authentication.CTraderOpenApiClient;
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

    private void givenConnectedConnection() {
        BrokerConnection connection = BrokerConnection.create(ACCOUNT, OWNER, BrokerProviderId.CTRADER, NOW);
        connection.bindExternalAccount("1001", "demo", NOW);
        connection.connected(CREDENTIAL, Set.of(BrokerPermission.READ_ACCOUNT, BrokerPermission.TRADE), "1001", null, NOW);
        connection.confirmProviderAccountEnvironment(false, NOW);
        when(connections.findByBrokerAccountId(ACCOUNT)).thenReturn(Optional.of(connection));
        when(secrets.read(any(CredentialReference.class))).thenAnswer(invocation -> new PlainSecret(
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
