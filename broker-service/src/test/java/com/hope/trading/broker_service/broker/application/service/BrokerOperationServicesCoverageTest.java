package com.hope.trading.broker_service.broker.application.service;

import com.hope.trading.broker_service.broker.application.registry.BrokerProviderRegistry;
import com.hope.trading.broker_service.broker.domain.capability.BrokerCapabilities.*;
import com.hope.trading.broker_service.broker.domain.model.BrokerModels;
import com.hope.trading.broker_service.broker.domain.provider.BrokerProvider;
import com.hope.trading.broker_service.broker.infrastructure.monitoring.BrokerOperationsMetrics;
import com.hope.trading.broker_service.connection.application.BrokerConnectionRepository;
import com.hope.trading.broker_service.connection.domain.BrokerConnection;
import com.hope.trading.broker_service.connection.domain.BrokerConnectionStatus;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import com.hope.trading.broker_service.connection.domain.BrokerProviderRegistrationCatalog;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class BrokerOperationServicesCoverageTest {
    private final UUID accountId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final BrokerConnectionRepository connections = mock(BrokerConnectionRepository.class);
    private final BrokerProvider provider = mock(BrokerProvider.class);
    private final BrokerOperationsMetrics metrics = new BrokerOperationsMetrics(
            new SimpleMeterRegistry(), ObservationRegistry.NOOP);
    private BrokerProviderResolver resolver;

    @BeforeEach
    void setupConnectedAccount() {
        when(provider.id()).thenReturn(BrokerProviderId.KRAKEN);
        resolver = new BrokerProviderResolver(connections, new BrokerProviderRegistry(List.of(provider)));
        BrokerConnection connection = mock(BrokerConnection.class);
        when(connection.provider()).thenReturn(BrokerProviderId.KRAKEN);
        when(connection.technicalStatus()).thenReturn(BrokerConnectionStatus.CONNECTED);
        when(connection.activeCredentialReference()).thenReturn(UUID.randomUUID());
        when(connections.findByBrokerAccountId(accountId)).thenReturn(Optional.of(connection));
    }

    @Test
    void delegatesReadAndProviderOperationsThroughTheirCapabilities() {
        AccountCapability account = mock(AccountCapability.class);
        PositionCapability positions = mock(PositionCapability.class);
        OrderCapability orders = mock(OrderCapability.class);
        TechnicalCapability technical = mock(TechnicalCapability.class);
        MarginCapability margin = mock(MarginCapability.class);
        when(provider.capability(AccountCapability.class)).thenReturn(Optional.of(account));
        when(provider.capability(PositionCapability.class)).thenReturn(Optional.of(positions));
        when(provider.capability(OrderCapability.class)).thenReturn(Optional.of(orders));
        when(provider.capability(TechnicalCapability.class)).thenReturn(Optional.of(technical));
        when(provider.capability(MarginCapability.class)).thenReturn(Optional.of(margin));

        assertThat(new BrokerOperationServices.GetAccountService(resolver, metrics).get(accountId)).isNull();
        assertThat(new BrokerOperationServices.GetPositionsService(resolver, metrics).get(accountId)).isEmpty();
        assertThat(new BrokerOperationServices.GetOrdersService(resolver, metrics).get(accountId)).isEmpty();

        BrokerProviderRegistry registry = new BrokerProviderRegistry(List.of(provider));
        var providerCapabilities = new BrokerOperationServices.GetProviderTechnicalCapabilitiesService(registry, metrics);
        assertThat(providerCapabilities.get(BrokerProviderId.KRAKEN, accountId, "EUR/USD")).isNull();
        assertThat(providerCapabilities.get(BrokerProviderId.KRAKEN, accountId, List.of("EUR/USD"))).isEmpty();
        assertThat(new BrokerOperationServices.PreviewProviderMarginService(registry, metrics)
                .preview(BrokerProviderId.KRAKEN, null)).isNull();

        verify(account).account(accountId);
        verify(positions).positions(accountId);
        verify(orders).orders(accountId);
        verify(technical).capabilities(accountId, "EUR/USD");
        verify(margin).preview(null);
    }

    @Test
    void enforcesOwnershipAndInputValidationForScopedServices() {
        when(connections.findByBrokerAccountIdAndOwnerId(accountId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new BrokerOperationServices.GetLatestAccountSnapshotV2Service(
                connections, mock(com.hope.trading.broker_service.broker.infrastructure.persistence.RiskSnapshotPersistence.class))
                .get(ownerId, accountId)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> new BrokerOperationServices.GetTechnicalCapabilitiesService(
                resolver, metrics, connections).get(ownerId, accountId, "EUR/USD"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> new BrokerOperationServices.GetRiskSnapshotService(
                resolver, metrics, connections).get(ownerId, accountId,
                        Instant.parse("2026-01-02T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void usesReusableAndAuthoritativeSnapshotsBeforeFallback() {
        BrokerConnection connection = mock(BrokerConnection.class);
        var snapshots = mock(com.hope.trading.broker_service.broker.infrastructure.persistence.RiskSnapshotPersistence.class);
        var registrations = new BrokerProviderRegistrationCatalog();
        when(connection.provider()).thenReturn(BrokerProviderId.KRAKEN);
        when(connection.externalAccountId()).thenReturn("external-1");
        when(connections.findByBrokerAccountIdAndOwnerId(accountId, ownerId)).thenReturn(Optional.of(connection));
        when(snapshots.latestFresh(any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        when(provider.capability(AuthoritativeSnapshotCapability.class)).thenReturn(Optional.of(mock(AuthoritativeSnapshotCapability.class)));

        var service = new BrokerOperationServices.GetAccountSnapshotV2Service(
                resolver, metrics, connections, registrations, snapshots,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        assertThat(service.get(ownerId, accountId)).isNull();
        verify(snapshots).latestFresh(eq(accountId), any(), eq("external-1"), any(), any());
    }

    @Test
    void delegatesScopedExecutionRiskTechnicalAndMarginOperations() {
        BrokerConnection connection = mock(BrokerConnection.class);
        when(connection.provider()).thenReturn(BrokerProviderId.KRAKEN);
        when(connection.technicalStatus()).thenReturn(BrokerConnectionStatus.CONNECTED);
        when(connection.activeCredentialReference()).thenReturn(UUID.randomUUID());
        when(connections.findByBrokerAccountIdAndOwnerId(accountId, ownerId)).thenReturn(Optional.of(connection));
        when(connections.findByBrokerAccountId(accountId)).thenReturn(Optional.of(connection));

        ExecutionCapability execution = mock(ExecutionCapability.class);
        ReconciliationCapability reconciliation = mock(ReconciliationCapability.class);
        RiskSnapshotCapability risk = mock(RiskSnapshotCapability.class);
        TechnicalCapability technical = mock(TechnicalCapability.class);
        MarginCapability margin = mock(MarginCapability.class);
        when(provider.capability(ExecutionCapability.class)).thenReturn(Optional.of(execution));
        when(provider.capability(ReconciliationCapability.class)).thenReturn(Optional.of(reconciliation));
        when(provider.capability(RiskSnapshotCapability.class)).thenReturn(Optional.of(risk));
        when(provider.capability(TechnicalCapability.class)).thenReturn(Optional.of(technical));
        when(provider.capability(MarginCapability.class)).thenReturn(Optional.of(margin));
        OrderCapability orders = ordersNeverUsed();

        var executionRequest = new BrokerModels.ExecutionRequest(UUID.randomUUID(), UUID.randomUUID(),
                "idem", accountId, "EUR/USD", BrokerModels.Side.BUY,
                BrokerModels.OrderType.MARKET, BigDecimal.ONE, null);
        var reconciliationRequest = new BrokerModels.ReconciliationRequest(
                UUID.randomUUID(), UUID.randomUUID(), "idem", accountId);
        var from = Instant.parse("2026-01-01T00:00:00Z");
        var to = Instant.parse("2026-01-02T00:00:00Z");
        var marginRequest = new BrokerModels.MarginPreviewRequest(accountId, "EUR/USD",
                BrokerModels.Side.BUY, BigDecimal.ONE, BigDecimal.ONE, null);

        assertThat(new BrokerOperationServices.ExecuteOrderService(resolver, metrics, connections)
                .execute(executionRequest, ownerId)).isNull();
        new BrokerOperationServices.CancelOrderService(resolver, metrics, connections)
                .cancel(accountId, "order-1", ownerId);
        assertThat(new BrokerOperationServices.ReconcileExecutionService(resolver, metrics, connections)
                .reconcile(reconciliationRequest, ownerId)).isNull();
        assertThat(new BrokerOperationServices.GetRiskSnapshotService(resolver, metrics, connections)
                .get(ownerId, accountId, from, to)).isNull();
        assertThat(new BrokerOperationServices.GetTechnicalCapabilitiesService(resolver, metrics, connections)
                .get(ownerId, accountId, "EUR/USD")).isNull();
        assertThat(new BrokerOperationServices.GetTechnicalCapabilitiesService(resolver, metrics, connections)
                .get(ownerId, accountId, List.of("EUR/USD"))).isEmpty();
        assertThat(new BrokerOperationServices.PreviewMarginService(resolver, metrics, connections)
                .preview(marginRequest, ownerId)).isNull();

        verify(execution).execute(executionRequest);
        verify(orders).cancel(accountId, "order-1");
        verify(reconciliation).reconcile(reconciliationRequest);
        verify(risk).snapshot(accountId, from, to);
        verify(technical).capabilities(accountId, "EUR/USD");
        verify(margin).preview(marginRequest);
    }

    private OrderCapability ordersNeverUsed() {
        OrderCapability orders = mock(OrderCapability.class);
        when(provider.capability(OrderCapability.class)).thenReturn(Optional.of(orders));
        return orders;
    }
}
