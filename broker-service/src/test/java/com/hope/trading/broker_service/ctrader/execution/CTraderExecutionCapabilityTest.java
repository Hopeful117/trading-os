package com.hope.trading.broker_service.ctrader.execution;

import com.hope.trading.broker_service.broker.domain.model.BrokerModels;
import com.hope.trading.broker_service.connection.application.BrokerConnectionRepository;
import com.hope.trading.broker_service.connection.domain.BrokerConnection;
import com.hope.trading.broker_service.connection.domain.BrokerPermission;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import com.hope.trading.broker_service.ctrader.authentication.CTraderOpenApiClient;
import com.hope.trading.broker_service.ctrader.authentication.CTraderAuthenticationException;
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
    void submitsWhenFreshOpenMarketTradabilityEvidenceIsPresent() {
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
                BigDecimal.ONE, BigDecimal.valueOf(.99), "OPEN", true, NOW));

        assertInstanceOf(BrokerModels.Acknowledged.class, result);
    }

    @Test
    void rejectsExecutionWhenMarketTradabilityIsUnknown() {
        var result = capability().execute(new BrokerModels.ExecutionRequest(
                UUID.randomUUID(), UUID.randomUUID(), "idempotency", ACCOUNT, "EURUSD",
                BrokerModels.Side.BUY, BrokerModels.OrderType.LIMIT, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.valueOf(.99)));

        assertThat(((BrokerModels.Rejected) result).reasonCode())
                .isEqualTo("CTRADER_MARKET_TRADABILITY_UNKNOWN");
    }

    @Test
    void rejectsExecutionWhenMarketStateEvidenceIsFromTheFuture() {
        var result = capability().execute(new BrokerModels.ExecutionRequest(
                UUID.randomUUID(), UUID.randomUUID(), "idempotency", ACCOUNT, "EURUSD",
                BrokerModels.Side.BUY, BrokerModels.OrderType.LIMIT, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.valueOf(.99), "OPEN", true, NOW.plusSeconds(1)));

        assertThat(((BrokerModels.Rejected) result).reasonCode())
                .isEqualTo("CTRADER_MARKET_TRADABILITY_UNKNOWN");
        verifyNoInteractions(connections, secrets, client);
    }

    @Test
    void rejectsLiveAccountEvenWhenTradePermissionIsPresent() {
        var result = capability().execute(new BrokerModels.ExecutionRequest(
                UUID.randomUUID(), UUID.randomUUID(), "idempotency", ACCOUNT, "EURUSD",
                BrokerModels.Side.BUY, BrokerModels.OrderType.LIMIT, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.valueOf(.99)));

        assertInstanceOf(BrokerModels.Rejected.class, result);
    }

    @Test
    void rejectsFtmoFreeExecutionUntilMarketTradabilityIsAuthoritative() {
        var result = new CTraderExecutionCapability(connections, secrets, client,
                new CTraderProperties("client", "secret", "https://callback", "https://auth", "demo",
                        "FTMO_FREE", "https://token", "wss://demo.ctraderapi.com:5036", 600),
                Clock.fixed(NOW, ZoneOffset.UTC), enabledSafety()).execute(new BrokerModels.ExecutionRequest(
                UUID.randomUUID(), UUID.randomUUID(), "idempotency", ACCOUNT, "EURUSD",
                BrokerModels.Side.BUY, BrokerModels.OrderType.LIMIT, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.valueOf(.99)));

        assertThat(((BrokerModels.Rejected) result).reasonCode())
                .isEqualTo("CTRADER_MARKET_TRADABILITY_UNKNOWN");
    }

    @Test
    void rejectsProtectedMarketExecutionWhenMarketTradabilityIsUnknown() {
        var result = capability().execute(new BrokerModels.ExecutionRequest(
                UUID.randomUUID(), UUID.randomUUID(), "idempotency", ACCOUNT, "EURUSD",
                BrokerModels.Side.BUY, BrokerModels.OrderType.MARKET, BigDecimal.ONE, null,
                BigDecimal.valueOf(.99), List.of(BigDecimal.valueOf(1.02)), BigDecimal.ONE));

        assertThat(((BrokerModels.Rejected) result).reasonCode())
                .isEqualTo("CTRADER_MARKET_TRADABILITY_UNKNOWN");
        verifyNoInteractions(connections, secrets, client);
    }

    @Test
    void rejectsProtectedMarketExecutionWithoutReferencePriceBeforeProviderAccess() {
        var result = capability().execute(new BrokerModels.ExecutionRequest(
                UUID.randomUUID(), UUID.randomUUID(), "idempotency", ACCOUNT, "EURUSD",
                BrokerModels.Side.BUY, BrokerModels.OrderType.MARKET, BigDecimal.ONE, null,
                BigDecimal.valueOf(.99), List.of(BigDecimal.valueOf(1.02))));

        assertInstanceOf(BrokerModels.Rejected.class, result);
        assertThat(((BrokerModels.Rejected) result).reasonCode())
                .isEqualTo("CTRADER_MARKET_PROTECTION_REFERENCE_REQUIRED");
        verifyNoInteractions(connections, secrets, client);
    }

    @Test
    void rejectsExecutionWhenConnectionIsMissing() {
        when(connections.findByBrokerAccountId(ACCOUNT)).thenReturn(Optional.empty());
        var result = capability().execute(requestWithFreshEvidence());
        assertThat(result).isInstanceOf(BrokerModels.Unknown.class);
    }

    @Test
    void rejectsExecutionWhenTokenIsExpired() {
        when(connections.findByBrokerAccountId(ACCOUNT)).thenReturn(Optional.of(connected(true, false)));
        when(secrets.read(any())).thenReturn(new PlainSecret(
                CTraderTokenCodec.encode(new CTraderToken("access", "refresh", NOW.minusSeconds(1)))));
        var result = capability().execute(requestWithFreshEvidence());
        assertThat(((BrokerModels.Rejected) result).reasonCode()).contains("AUTHENTICATION");
    }

    @Test
    void rejectsExecutionWhenObservationIsIncomplete() {
        when(connections.findByBrokerAccountId(ACCOUNT)).thenReturn(Optional.of(connected(true, false)));
        when(secrets.read(any())).thenAnswer(invocation -> new PlainSecret(
                CTraderTokenCodec.encode(new CTraderToken("access", "refresh", NOW.plusSeconds(3600)))));
        when(client.readOnlyObservation(any(), any(), any(), any())).thenReturn(observation(false, NOW));
        var result = capability().execute(requestWithFreshEvidence());
        assertThat(((BrokerModels.Rejected) result).reasonCode())
                .isEqualTo("CTRADER_AUTHORITATIVE_SNAPSHOT_IS_NOT_FRESH_AND_COMPLETE");
    }

    @Test
    void unknownProviderFailureReturnsUnknownResult() {
        givenConnectedObservation();
        when(client.submitOrder(any(), any())).thenThrow(new CTraderAuthenticationException("transport timeout"));
        var result = capability().execute(requestWithFreshEvidence());
        assertThat(result).isInstanceOf(BrokerModels.Unknown.class);
    }

    @Test
    void providerRejectionReturnsRejectedResult() {
        givenConnectedObservation();
        when(client.submitOrder(any(), any())).thenThrow(new CTraderAuthenticationException("invalid credentials"));
        var result = capability().execute(requestWithFreshEvidence());
        assertThat(result).isInstanceOf(BrokerModels.Rejected.class);
    }

    @Test
    void reconciliationConfirmsAbsentOrder() {
        givenConnectedObservation();
        var result = capability().reconcile(new BrokerModels.ReconciliationRequest(
                UUID.randomUUID(), UUID.randomUUID(), "idempotency", ACCOUNT));
        assertThat(result).isInstanceOf(BrokerModels.ConfirmedAbsent.class);
    }

    @Test
    void reconciliationReportsMultipleMatchingOrdersAsInconsistent() {
        when(connections.findByBrokerAccountId(ACCOUNT)).thenReturn(Optional.of(connected(true, false)));
        when(secrets.read(any())).thenAnswer(invocation -> new PlainSecret(
                CTraderTokenCodec.encode(new CTraderToken("access", "refresh", NOW.plusSeconds(3600)))));
        String clientId = java.util.UUID.nameUUIDFromBytes("idempotency".getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        var order = new CTraderReadOnlyObservation.Order("order-1", clientId, "FILLED", BigDecimal.ONE,
                BigDecimal.ONE, NOW);
        var second = new CTraderReadOnlyObservation.Order("order-2", clientId, "FILLED", BigDecimal.ONE,
                BigDecimal.ONE, NOW);
        when(client.readOnlyObservation(any(), any(), any(), any())).thenReturn(observation(true, NOW, List.of(order, second)));
        var result = capability().reconcile(new BrokerModels.ReconciliationRequest(
                UUID.randomUUID(), UUID.randomUUID(), "idempotency", ACCOUNT));
        assertThat(result).isInstanceOf(BrokerModels.Inconsistent.class);
    }

    @Test
    void reconciliationMapsSingleMatchingOrderStatuses() {
        when(connections.findByBrokerAccountId(ACCOUNT)).thenReturn(Optional.of(connected(true, false)));
        when(secrets.read(any())).thenAnswer(invocation -> new PlainSecret(
                CTraderTokenCodec.encode(new CTraderToken("access", "refresh", NOW.plusSeconds(3600)))));
        String clientId = java.util.UUID.nameUUIDFromBytes("idempotency".getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        String[] statuses = {"ACCEPTED", "FILLED", "REJECTED", "CANCELLED", "unexpected"};
        int[] index = {0};
        when(client.readOnlyObservation(any(), any(), any(), any())).thenAnswer(invocation -> {
            String status = statuses[index[0]++];
            var order = new CTraderReadOnlyObservation.Order("order-1", clientId, status,
                    BigDecimal.ONE, BigDecimal.ONE, NOW);
            return observation(true, NOW, List.of(order));
        });

        for (String status : statuses) {
            var result = capability().reconcile(new BrokerModels.ReconciliationRequest(
                    UUID.randomUUID(), UUID.randomUUID(), "idempotency", ACCOUNT));

            assertThat(result).as(status).isInstanceOf(BrokerModels.ReconciledOrder.class);
        }
    }

    private BrokerModels.ExecutionRequest requestWithFreshEvidence() {
        return new BrokerModels.ExecutionRequest(UUID.randomUUID(), UUID.randomUUID(), "idempotency", ACCOUNT,
                "EURUSD", BrokerModels.Side.BUY, BrokerModels.OrderType.LIMIT, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.valueOf(.99), "OPEN", true, NOW);
    }

    private void givenConnectedObservation() {
        when(connections.findByBrokerAccountId(ACCOUNT)).thenReturn(Optional.of(connected(true, false)));
        when(secrets.read(any())).thenReturn(new PlainSecret(
                CTraderTokenCodec.encode(new CTraderToken("access", "refresh", NOW.plusSeconds(3600)))));
        when(client.readOnlyObservation(any(), any(), any(), any())).thenReturn(observation(true, NOW));
    }

    private CTraderReadOnlyObservation observation(boolean complete, Instant completedAt) {
        return observation(complete, completedAt, List.of());
    }

    private CTraderReadOnlyObservation observation(boolean complete, Instant completedAt,
                                                   List<CTraderReadOnlyObservation.Order> orders) {
        return new CTraderReadOnlyObservation("1001", "USD", BigDecimal.valueOf(50000),
                BigDecimal.valueOf(50000), BigDecimal.ZERO, BigDecimal.valueOf(50000),
                List.of(), orders, List.of(), NOW, NOW, completedAt, complete, List.of());
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
