package com.hope.trading.broker_service.ctrader.execution;

import com.hope.trading.broker_service.broker.domain.model.BrokerModels;
import com.hope.trading.broker_service.connection.application.BrokerConnectionRepository;
import com.hope.trading.broker_service.connection.domain.BrokerConnection;
import com.hope.trading.broker_service.connection.domain.BrokerPermission;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import com.hope.trading.broker_service.ctrader.authentication.CTraderOpenApiClient;
import com.hope.trading.broker_service.ctrader.config.CTraderProperties;
import com.hope.trading.broker_service.ctrader.config.CTraderSafetyProperties;
import com.hope.trading.broker_service.ctrader.authentication.CTraderToken;
import com.hope.trading.broker_service.ctrader.authentication.CTraderTokenCodec;
import com.hope.trading.broker_service.ctrader.synchronization.CTraderReadOnlyObservation;
import com.hope.trading.broker_service.secret.application.SecretReader;
import com.hope.trading.broker_service.secret.domain.CredentialReference;
import com.hope.trading.broker_service.secret.domain.PlainSecret;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CTraderExecutionCapabilityTest {
    private static final Instant NOW = Instant.parse("2026-10-27T04:00:00Z");
    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID CREDENTIAL = UUID.randomUUID();

    @Mock BrokerConnectionRepository connections;
    @Mock SecretReader secrets;
    @Mock CTraderOpenApiClient client;

    @Test
    void safetyPolicyRejectsExecutionBeforeResolvingConnection() {
        var result = new CTraderExecutionCapability(connections, secrets, client,
                new CTraderProperties("client", "secret", "https://callback", "https://auth", "demo",
                        "https://token", "wss://demo.ctraderapi.com:5036", 600),
                Clock.fixed(NOW, ZoneOffset.UTC),
                new CTraderSafetyProperties(true, false, 1000, 700, 800,
                        Duration.ofMinutes(20), Duration.ofMinutes(30))).execute(new BrokerModels.ExecutionRequest(
                UUID.randomUUID(), UUID.randomUUID(), "idempotency", ACCOUNT, "EURUSD",
                BrokerModels.Side.BUY, BrokerModels.OrderType.LIMIT, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.valueOf(.99)));

        assertInstanceOf(BrokerModels.Rejected.class, result);
        verifyNoInteractions(connections, secrets, client);
    }

    @Test
    void submitsOnlyToAConnectedProviderConfirmedDemoWithTradePermission() {
        BrokerConnection connection = connected(true, false);
        when(connections.findByBrokerAccountId(ACCOUNT)).thenReturn(Optional.of(connection));
        when(secrets.read(any(CredentialReference.class))).thenReturn(new PlainSecret(
                CTraderTokenCodec.encode(new CTraderToken("access", "refresh", NOW.plusSeconds(3600)))));
        when(client.readOnlyObservation(any(), any(), any(), any())).thenReturn(
                new CTraderReadOnlyObservation("1001", "USD", BigDecimal.valueOf(50000),
                        BigDecimal.valueOf(50000), BigDecimal.ZERO, BigDecimal.valueOf(50000),
                        List.of(), List.of(), List.of(), NOW, NOW, NOW, true, List.of()));
        when(client.submitOrder(any(), any())).thenReturn(new CTraderOpenApiClient.CTraderOrderResponse("order-1"));

        var result = capability().execute(new BrokerModels.ExecutionRequest(
                UUID.randomUUID(), UUID.randomUUID(), "idempotency", ACCOUNT, "EURUSD",
                BrokerModels.Side.BUY, BrokerModels.OrderType.LIMIT, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.valueOf(.99)));

        assertInstanceOf(BrokerModels.Acknowledged.class, result);
    }

    @Test
    void rejectsLiveAccountEvenWhenTradePermissionIsPresent() {
        BrokerConnection connection = connected(true, true);
        when(connections.findByBrokerAccountId(ACCOUNT)).thenReturn(Optional.of(connection));

        var result = capability().execute(new BrokerModels.ExecutionRequest(
                UUID.randomUUID(), UUID.randomUUID(), "idempotency", ACCOUNT, "EURUSD",
                BrokerModels.Side.BUY, BrokerModels.OrderType.LIMIT, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.valueOf(.99)));

        assertInstanceOf(BrokerModels.Rejected.class, result);
    }

    @Test
    void acceptsProviderLiveFlagForExplicitFtmoFreeSimulatedProgram() {
        BrokerConnection connection = connected(true, true);
        when(connections.findByBrokerAccountId(ACCOUNT)).thenReturn(Optional.of(connection));
        when(secrets.read(any(CredentialReference.class))).thenReturn(new PlainSecret(
                CTraderTokenCodec.encode(new CTraderToken("access", "refresh", NOW.plusSeconds(3600)))));
        when(client.readOnlyObservation(any(), any(), any(), any())).thenReturn(
                new CTraderReadOnlyObservation("1001", "USD", BigDecimal.valueOf(50000),
                        BigDecimal.valueOf(50000), BigDecimal.ZERO, BigDecimal.valueOf(50000),
                        List.of(), List.of(), List.of(), NOW, NOW, NOW, true, List.of()));
        when(client.submitOrder(any(), any())).thenReturn(new CTraderOpenApiClient.CTraderOrderResponse("order-1"));

        var result = new CTraderExecutionCapability(connections, secrets, client,
                new CTraderProperties("client", "secret", "https://callback", "https://auth", "demo",
                        "FTMO_FREE", "https://token", "wss://demo.ctraderapi.com:5036", 600),
                Clock.fixed(NOW, ZoneOffset.UTC), enabledSafety()).execute(new BrokerModels.ExecutionRequest(
                UUID.randomUUID(), UUID.randomUUID(), "idempotency", ACCOUNT, "EURUSD",
                BrokerModels.Side.BUY, BrokerModels.OrderType.LIMIT, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.valueOf(.99)));

        assertInstanceOf(BrokerModels.Acknowledged.class, result);
    }

    @Test
    void propagatesProtectiveTargetsForMarketExecution() {
        BrokerConnection connection = connected(true, false);
        when(connections.findByBrokerAccountId(ACCOUNT)).thenReturn(Optional.of(connection));
        when(secrets.read(any(CredentialReference.class))).thenReturn(new PlainSecret(
                CTraderTokenCodec.encode(new CTraderToken("access", "refresh", NOW.plusSeconds(3600)))));
        when(client.readOnlyObservation(any(), any(), any(), any())).thenReturn(
                new CTraderReadOnlyObservation("1001", "USD", BigDecimal.valueOf(50000),
                        BigDecimal.valueOf(50000), BigDecimal.ZERO, BigDecimal.valueOf(50000),
                        List.of(), List.of(), List.of(), NOW, NOW, NOW, true, List.of()));
        when(client.submitOrder(any(), any())).thenReturn(new CTraderOpenApiClient.CTraderOrderResponse("order-1"));

        var result = capability().execute(new BrokerModels.ExecutionRequest(
                UUID.randomUUID(), UUID.randomUUID(), "idempotency", ACCOUNT, "EURUSD",
                BrokerModels.Side.BUY, BrokerModels.OrderType.MARKET, BigDecimal.ONE, null,
                BigDecimal.valueOf(.99), List.of(BigDecimal.valueOf(1.02))));

        assertInstanceOf(BrokerModels.Acknowledged.class, result);
        var request = verifyAndCaptureOrderRequest();
        assertThat(request.orderType()).isEqualTo("MARKET");
        assertThat(request.stopLossPrice()).isEqualByComparingTo("0.99");
        assertThat(request.takeProfitPrices()).containsExactly(BigDecimal.valueOf(1.02));
    }

    private CTraderOpenApiClient.CTraderOrderRequest verifyAndCaptureOrderRequest() {
        var captor = org.mockito.ArgumentCaptor.forClass(CTraderOpenApiClient.CTraderOrderRequest.class);
        verify(client).submitOrder(any(), captor.capture());
        return captor.getValue();
    }

    private BrokerConnection connected(boolean trade, boolean live) {
        BrokerConnection connection = BrokerConnection.create(ACCOUNT, OWNER, BrokerProviderId.CTRADER, NOW);
        Set<BrokerPermission> permissions = trade ? Set.of(BrokerPermission.READ_ACCOUNT, BrokerPermission.TRADE)
                : Set.of(BrokerPermission.READ_ACCOUNT);
        connection.bindExternalAccount("1001", "demo", NOW);
        connection.connected(CREDENTIAL, permissions, "1001", null, NOW);
        connection.confirmProviderAccountEnvironment(live, NOW);
        return connection;
    }

    private CTraderExecutionCapability capability() {
        return new CTraderExecutionCapability(connections, secrets, client,
                new CTraderProperties("client", "secret", "https://callback", "https://auth", "demo",
                        "https://token", "wss://demo.ctraderapi.com:5036", 600),
                Clock.fixed(NOW, ZoneOffset.UTC), enabledSafety());
    }

    private CTraderSafetyProperties enabledSafety() {
        return new CTraderSafetyProperties(true, true, 1000, 700, 800,
                Duration.ofMinutes(20), Duration.ofMinutes(30));
    }
}
