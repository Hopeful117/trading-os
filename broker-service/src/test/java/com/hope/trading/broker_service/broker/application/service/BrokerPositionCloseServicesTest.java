package com.hope.trading.broker_service.broker.application.service;

import com.hope.trading.broker_service.broker.application.registry.BrokerProviderRegistry;
import com.hope.trading.broker_service.broker.application.service.BrokerOperationServices.*;
import com.hope.trading.broker_service.broker.api.controller.PositionManagementController;
import com.hope.trading.broker_service.broker.api.dto.PositionCloseApiDtos.*;
import com.hope.trading.broker_service.broker.domain.capability.BrokerCapabilities.PositionManagementCapability;
import com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.*;
import com.hope.trading.broker_service.broker.domain.model.BrokerModels.*;
import com.hope.trading.broker_service.broker.domain.provider.BrokerProvider;
import com.hope.trading.broker_service.broker.infrastructure.monitoring.BrokerOperationsMetrics;
import com.hope.trading.broker_service.connection.application.BrokerConnectionRepository;
import com.hope.trading.broker_service.connection.domain.BrokerConnection;
import com.hope.trading.broker_service.connection.domain.BrokerConnectionStatus;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import com.hope.trading.broker_service.security.BrokerPrincipal;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BrokerPositionCloseServicesTest {

    @Mock private BrokerConnectionRepository connections;
    @Mock private BrokerProvider provider;

    private final UUID brokerAccountId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();

    private BrokerOperationsMetrics metrics;
    private BrokerProviderResolver resolver;

    @BeforeEach
    void setUp() {
        when(provider.id()).thenReturn(BrokerProviderId.KRAKEN);
        metrics = new BrokerOperationsMetrics(new SimpleMeterRegistry(), ObservationRegistry.NOOP);
        resolver = new BrokerProviderResolver(connections, new BrokerProviderRegistry(List.of(provider)));
    }

    private void allowOwnership(UUID accountId, UUID owner) {
        when(connections.findByBrokerAccountIdAndOwnerId(accountId, owner))
                .thenReturn(Optional.of(mock(BrokerConnection.class)));
    }

    private void denyOwnership(UUID accountId, UUID owner) {
        when(connections.findByBrokerAccountIdAndOwnerId(accountId, owner))
                .thenReturn(Optional.empty());
    }

    private void setupProviderWithCapability() {
        BrokerConnection conn = mock(BrokerConnection.class);
        when(conn.technicalStatus()).thenReturn(BrokerConnectionStatus.CONNECTED);
        when(conn.activeCredentialReference()).thenReturn(UUID.randomUUID());
        when(conn.provider()).thenReturn(BrokerProviderId.KRAKEN);
        when(connections.findByBrokerAccountId(brokerAccountId)).thenReturn(Optional.of(conn));
        when(provider.capability(PositionManagementCapability.class))
                .thenReturn(Optional.of(capability()));
    }

    private void setupProviderWithoutCapability() {
        BrokerConnection conn = mock(BrokerConnection.class);
        when(conn.technicalStatus()).thenReturn(BrokerConnectionStatus.CONNECTED);
        when(conn.activeCredentialReference()).thenReturn(UUID.randomUUID());
        when(conn.provider()).thenReturn(BrokerProviderId.KRAKEN);
        when(connections.findByBrokerAccountId(brokerAccountId)).thenReturn(Optional.of(conn));
        when(provider.capability(PositionManagementCapability.class))
                .thenReturn(Optional.empty());
    }

    @SuppressWarnings("unchecked")
    private PositionManagementCapability capability() {
        return mock(PositionManagementCapability.class);
    }

    // ─── ResolveTargetService ────────────────────────────────────────────

    @Nested
    class ResolveTargetServiceTests {

        @Test
        void resolveDelegatesToCapability() {
            allowOwnership(brokerAccountId, ownerId);
            setupProviderWithCapability();
            PositionManagementCapability cap = provider.capability(PositionManagementCapability.class).orElseThrow();

            ResolveTargetRequest request = new ResolveTargetRequest(brokerAccountId, "pos-ref-1");
            ResolvedPositionCloseTarget expected = new ResolvedPositionCloseTarget(brokerAccountId, "scope-1");
            when(cap.resolveTarget(request)).thenReturn(expected);

            ResolveTargetService service = new ResolveTargetService(resolver, metrics, connections);
            ResolvedPositionCloseTarget result = service.resolve(request, ownerId);

            assertThat(result).isEqualTo(expected);
            verify(connections).findByBrokerAccountIdAndOwnerId(brokerAccountId, ownerId);
            verify(cap).resolveTarget(request);
        }

        @Test
        void resolveThrowsAuthorizationExceptionWhenOwnershipDenied() {
            denyOwnership(brokerAccountId, otherUserId);

            ResolveTargetRequest request = new ResolveTargetRequest(brokerAccountId, "pos-ref-1");

            ResolveTargetService service = new ResolveTargetService(resolver, metrics, connections);
            assertThatThrownBy(() -> service.resolve(request, otherUserId))
                    .isInstanceOf(BrokerAuthorizationException.class)
                    .hasMessage("Broker account is not accessible");
        }

        @Test
        void resolveThrowsUnsupportedBrokerProviderExceptionWhenCapabilityMissing() {
            allowOwnership(brokerAccountId, ownerId);
            setupProviderWithoutCapability();

            ResolveTargetRequest request = new ResolveTargetRequest(brokerAccountId, "pos-ref-1");

            ResolveTargetService service = new ResolveTargetService(resolver, metrics, connections);
            assertThatThrownBy(() -> service.resolve(request, ownerId))
                    .isInstanceOf(UnsupportedBrokerProviderException.class);
        }
    }

    // ─── ExecuteCloseService ─────────────────────────────────────────────

    @Nested
    class ExecuteCloseServiceTests {

        @Test
        void executeDelegatesToCapability() {
            allowOwnership(brokerAccountId, ownerId);
            setupProviderWithCapability();
            PositionManagementCapability cap = provider.capability(PositionManagementCapability.class).orElseThrow();

            ExecuteCloseRequest request = new ExecuteCloseRequest(brokerAccountId, "scope-1", "idem-1");
            CloseAcknowledged expected = new CloseAcknowledged("ext-order-1", "corr-1");
            when(cap.executeClose(request)).thenReturn(expected);

            ExecuteCloseService service = new ExecuteCloseService(resolver, metrics, connections);
            CloseResult result = service.execute(request, ownerId);

            assertThat(result).isEqualTo(expected);
            verify(connections).findByBrokerAccountIdAndOwnerId(brokerAccountId, ownerId);
            verify(cap).executeClose(request);
        }

        @Test
        void executeThrowsAuthorizationExceptionWhenOwnershipDenied() {
            denyOwnership(brokerAccountId, otherUserId);

            ExecuteCloseRequest request = new ExecuteCloseRequest(brokerAccountId, "scope-1", "idem-1");

            ExecuteCloseService service = new ExecuteCloseService(resolver, metrics, connections);
            assertThatThrownBy(() -> service.execute(request, otherUserId))
                    .isInstanceOf(BrokerAuthorizationException.class)
                    .hasMessage("Broker account is not accessible");
        }

        @Test
        void executeThrowsUnsupportedBrokerProviderExceptionWhenCapabilityMissing() {
            allowOwnership(brokerAccountId, ownerId);
            setupProviderWithoutCapability();

            ExecuteCloseRequest request = new ExecuteCloseRequest(brokerAccountId, "scope-1", "idem-1");

            ExecuteCloseService service = new ExecuteCloseService(resolver, metrics, connections);
            assertThatThrownBy(() -> service.execute(request, ownerId))
                    .isInstanceOf(UnsupportedBrokerProviderException.class);
        }
    }

    // ─── ReconcileCloseService ───────────────────────────────────────────

    @Nested
    class ReconcileCloseServiceTests {

        @Test
        void reconcileDelegatesToCapability() {
            allowOwnership(brokerAccountId, ownerId);
            setupProviderWithCapability();
            PositionManagementCapability cap = provider.capability(PositionManagementCapability.class).orElseThrow();

            ReconcileCloseRequest request = new ReconcileCloseRequest(brokerAccountId, "scope-1", "idem-1");
            ExposureConfirmedAbsent expected = new ExposureConfirmedAbsent();
            when(cap.reconcile(request)).thenReturn(expected);

            ReconcileCloseService service = new ReconcileCloseService(resolver, metrics, connections);
            ReconciliationCloseResult result = service.reconcile(request, ownerId);

            assertThat(result).isEqualTo(expected);
            verify(connections).findByBrokerAccountIdAndOwnerId(brokerAccountId, ownerId);
            verify(cap).reconcile(request);
        }

        @Test
        void reconcileThrowsAuthorizationExceptionWhenOwnershipDenied() {
            denyOwnership(brokerAccountId, otherUserId);

            ReconcileCloseRequest request = new ReconcileCloseRequest(brokerAccountId, "scope-1", "idem-1");

            ReconcileCloseService service = new ReconcileCloseService(resolver, metrics, connections);
            assertThatThrownBy(() -> service.reconcile(request, otherUserId))
                    .isInstanceOf(BrokerAuthorizationException.class)
                    .hasMessage("Broker account is not accessible");
        }

        @Test
        void reconcileThrowsUnsupportedBrokerProviderExceptionWhenCapabilityMissing() {
            allowOwnership(brokerAccountId, ownerId);
            setupProviderWithoutCapability();

            ReconcileCloseRequest request = new ReconcileCloseRequest(brokerAccountId, "scope-1", "idem-1");

            ReconcileCloseService service = new ReconcileCloseService(resolver, metrics, connections);
            assertThatThrownBy(() -> service.reconcile(request, ownerId))
                    .isInstanceOf(UnsupportedBrokerProviderException.class);
        }
    }

    // ─── BrokerModels records ────────────────────────────────────────────

    @Nested
    class BrokerModelsTests {

        @Test
        void resolveTargetRequestConstruction() {
            UUID id = UUID.randomUUID();
            ResolveTargetRequest req = new ResolveTargetRequest(id, "ref-1");
            assertThat(req.brokerAccountId()).isEqualTo(id);
            assertThat(req.brokerPositionReference()).isEqualTo("ref-1");
        }

        @Test
        void resolveTargetRequestRejectsNullAccountId() {
            assertThatThrownBy(() -> new ResolveTargetRequest(null, "ref-1"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void resolveTargetRequestRejectsNullReference() {
            assertThatThrownBy(() -> new ResolveTargetRequest(UUID.randomUUID(), null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void resolveTargetRequestRejectsBlankReference() {
            assertThatThrownBy(() -> new ResolveTargetRequest(UUID.randomUUID(), "  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void resolvedPositionCloseTargetConstruction() {
            UUID id = UUID.randomUUID();
            ResolvedPositionCloseTarget target = new ResolvedPositionCloseTarget(id, "scope-1");
            assertThat(target.brokerAccountId()).isEqualTo(id);
            assertThat(target.resolvedMutationScope()).isEqualTo("scope-1");
        }

        @Test
        void resolvedPositionCloseTargetRejectsNullAccountId() {
            assertThatThrownBy(() -> new ResolvedPositionCloseTarget(null, "scope-1"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void resolvedPositionCloseTargetRejectsNullScope() {
            assertThatThrownBy(() -> new ResolvedPositionCloseTarget(UUID.randomUUID(), null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void resolvedPositionCloseTargetRejectsBlankScope() {
            assertThatThrownBy(() -> new ResolvedPositionCloseTarget(UUID.randomUUID(), "  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void executeCloseRequestConstruction() {
            UUID id = UUID.randomUUID();
            ExecuteCloseRequest req = new ExecuteCloseRequest(id, "scope-1", "key-1");
            assertThat(req.brokerAccountId()).isEqualTo(id);
            assertThat(req.resolvedMutationScope()).isEqualTo("scope-1");
            assertThat(req.idempotencyKey()).isEqualTo("key-1");
        }

        @Test
        void executeCloseRequestRejectsNullAccountId() {
            assertThatThrownBy(() -> new ExecuteCloseRequest(null, "scope-1", "key-1"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void executeCloseRequestRejectsNullScope() {
            assertThatThrownBy(() -> new ExecuteCloseRequest(UUID.randomUUID(), null, "key-1"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void executeCloseRequestRejectsNullIdempotencyKey() {
            assertThatThrownBy(() -> new ExecuteCloseRequest(UUID.randomUUID(), "scope-1", null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void executeCloseRequestRejectsBlankScope() {
            assertThatThrownBy(() -> new ExecuteCloseRequest(UUID.randomUUID(), "  ", "key-1"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void executeCloseRequestRejectsBlankIdempotencyKey() {
            assertThatThrownBy(() -> new ExecuteCloseRequest(UUID.randomUUID(), "scope-1", "  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void closeAcknowledgedConstruction() {
            CloseAcknowledged ack = new CloseAcknowledged("ext-1", "corr-1");
            assertThat(ack.externalOrderId()).isEqualTo("ext-1");
            assertThat(ack.correlationId()).isEqualTo("corr-1");
        }

        @Test
        void closeAcknowledgedRejectsNullExternalOrderId() {
            assertThatThrownBy(() -> new CloseAcknowledged(null, "corr-1"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void closeAcknowledgedRejectsNullCorrelationId() {
            assertThatThrownBy(() -> new CloseAcknowledged("ext-1", null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void closeAcknowledgedRejectsBlankExternalOrderId() {
            assertThatThrownBy(() -> new CloseAcknowledged("  ", "corr-1"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void closeAcknowledgedRejectsBlankCorrelationId() {
            assertThatThrownBy(() -> new CloseAcknowledged("ext-1", "  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void closeRejectedConstruction() {
            CloseRejected rej = new CloseRejected("ext-1", "INSUFFICIENT_FUNDS");
            assertThat(rej.externalOrderId()).isEqualTo("ext-1");
            assertThat(rej.reasonCode()).isEqualTo("INSUFFICIENT_FUNDS");
        }

        @Test
        void closeRejectedRejectsNullExternalOrderId() {
            assertThatThrownBy(() -> new CloseRejected(null, "CODE"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void closeRejectedRejectsNullReasonCode() {
            assertThatThrownBy(() -> new CloseRejected("ext-1", null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void closeRejectedRejectsBlankReasonCode() {
            assertThatThrownBy(() -> new CloseRejected("ext-1", "  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void closeUnknownConstruction() {
            CloseUnknown unk = new CloseUnknown("TIMEOUT");
            assertThat(unk.reasonCode()).isEqualTo("TIMEOUT");
        }

        @Test
        void closeUnknownRejectsNullReasonCode() {
            assertThatThrownBy(() -> new CloseUnknown(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void closeUnknownRejectsBlankReasonCode() {
            assertThatThrownBy(() -> new CloseUnknown("  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void closeAcknowledgedEqualsByRecordComponents() {
            CloseAcknowledged a = new CloseAcknowledged("ext-1", "corr-1");
            CloseAcknowledged b = new CloseAcknowledged("ext-1", "corr-1");
            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        }

        @Test
        void closeRejectedEqualsByRecordComponents() {
            CloseRejected a = new CloseRejected("ext-1", "CODE");
            CloseRejected b = new CloseRejected("ext-1", "CODE");
            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        }

        @Test
        void closeUnknownEqualsByRecordComponents() {
            CloseUnknown a = new CloseUnknown("CODE");
            CloseUnknown b = new CloseUnknown("CODE");
            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        }

        @Test
        void reconcileCloseRequestConstruction() {
            UUID id = UUID.randomUUID();
            ReconcileCloseRequest req = new ReconcileCloseRequest(id, "scope-1", "key-1");
            assertThat(req.brokerAccountId()).isEqualTo(id);
            assertThat(req.resolvedMutationScope()).isEqualTo("scope-1");
            assertThat(req.idempotencyKey()).isEqualTo("key-1");
        }

        @Test
        void reconcileCloseRequestRejectsNullAccountId() {
            assertThatThrownBy(() -> new ReconcileCloseRequest(null, "scope-1", "key-1"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void reconcileCloseRequestRejectsNullScope() {
            assertThatThrownBy(() -> new ReconcileCloseRequest(UUID.randomUUID(), null, "key-1"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void reconcileCloseRequestRejectsNullIdempotencyKey() {
            assertThatThrownBy(() -> new ReconcileCloseRequest(UUID.randomUUID(), "scope-1", null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void reconcileCloseRequestRejectsBlankScope() {
            assertThatThrownBy(() -> new ReconcileCloseRequest(UUID.randomUUID(), "  ", "key-1"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void reconcileCloseRequestRejectsBlankIdempotencyKey() {
            assertThatThrownBy(() -> new ReconcileCloseRequest(UUID.randomUUID(), "scope-1", "  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void exposureConfirmedAbsentConstruction() {
            ExposureConfirmedAbsent result = new ExposureConfirmedAbsent();
            assertThat(result).isNotNull();
        }

        @Test
        void commandConfirmedNotExecutedConstruction() {
            CommandConfirmedNotExecuted result = new CommandConfirmedNotExecuted();
            assertThat(result).isNotNull();
        }

        @Test
        void reconcileCloseResultInconclusiveConstruction() {
            Inconclusive inconclusive = new Inconclusive("UNKNOWN_ERROR");
            assertThat(inconclusive.reasonCode()).isEqualTo("UNKNOWN_ERROR");
        }

        @Test
        void reconcileCloseResultInconclusiveRejectsNullReasonCode() {
            assertThatThrownBy(() -> new Inconclusive(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void reconcileCloseResultInconclusiveRejectsBlankReasonCode() {
            assertThatThrownBy(() -> new Inconclusive("  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void exposureConfirmedAbsentEqualsByRecordComponents() {
            ExposureConfirmedAbsent a = new ExposureConfirmedAbsent();
            ExposureConfirmedAbsent b = new ExposureConfirmedAbsent();
            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        }

        @Test
        void commandConfirmedNotExecutedEqualsByRecordComponents() {
            CommandConfirmedNotExecuted a = new CommandConfirmedNotExecuted();
            CommandConfirmedNotExecuted b = new CommandConfirmedNotExecuted();
            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        }

        @Test
        void inconclusiveEqualsByRecordComponents() {
            Inconclusive a = new Inconclusive("CODE");
            Inconclusive b = new Inconclusive("CODE");
            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        }

        @Test
        void closeResultSubtypesAreDistinct() {
            CloseAcknowledged ack = new CloseAcknowledged("ext-1", "corr-1");
            CloseRejected rej = new CloseRejected("ext-1", "CODE");
            CloseUnknown unk = new CloseUnknown("CODE");

            assertThat(ack).isNotEqualTo(rej);
            assertThat(ack).isNotEqualTo(unk);
            assertThat(rej).isNotEqualTo(unk);
        }

        @Test
        void reconciliationCloseResultSubtypesAreDistinct() {
            ExposureConfirmedAbsent absent = new ExposureConfirmedAbsent();
            CommandConfirmedNotExecuted notExec = new CommandConfirmedNotExecuted();
            Inconclusive inconclusive = new Inconclusive("CODE");

            assertThat(absent).isNotEqualTo(notExec);
            assertThat(absent).isNotEqualTo(inconclusive);
            assertThat(notExec).isNotEqualTo(inconclusive);
        }
    }

    // ─── PositionManagementController ────────────────────────────────────

    @Nested
    class PositionManagementControllerTests {

        private PositionManagementController controller;
        private ResolveTargetService resolveTargetService;
        private ExecuteCloseService executeCloseService;
        private ReconcileCloseService reconcileCloseService;

        @BeforeEach
        void setUp() {
            resolveTargetService = new ResolveTargetService(resolver, metrics, connections);
            executeCloseService = new ExecuteCloseService(resolver, metrics, connections);
            reconcileCloseService = new ReconcileCloseService(resolver, metrics, connections);
            controller = new PositionManagementController(
                    resolveTargetService, executeCloseService, reconcileCloseService);
        }

        @Test
        void resolveTargetReturnsOkWithMappedResponse() {
            allowOwnership(brokerAccountId, ownerId);
            setupProviderWithCapability();
            PositionManagementCapability cap = provider.capability(PositionManagementCapability.class).orElseThrow();

            ResolveTargetRequest request = new ResolveTargetRequest(brokerAccountId, "pos-ref-1");
            ResolvedPositionCloseTarget target = new ResolvedPositionCloseTarget(brokerAccountId, "scope-1");
            when(cap.resolveTarget(request)).thenReturn(target);

            ResolveTargetApiRequest apiRequest = new ResolveTargetApiRequest(brokerAccountId, "pos-ref-1");
            BrokerPrincipal principal = new BrokerPrincipal(ownerId, "user", "TRADER");

            ResponseEntity<ResolvedTargetApiResponse> response = controller.resolveTarget(apiRequest, principal);

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            ResolvedTargetApiResponse body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.brokerAccountId()).isEqualTo(brokerAccountId);
            assertThat(body.resolvedMutationScope()).isEqualTo("scope-1");
        }

        @Test
        void executeCloseReturnsOkWithAcknowledgedResponse() {
            allowOwnership(brokerAccountId, ownerId);
            setupProviderWithCapability();
            PositionManagementCapability cap = provider.capability(PositionManagementCapability.class).orElseThrow();

            ExecuteCloseRequest request = new ExecuteCloseRequest(brokerAccountId, "scope-1", "idem-1");
            CloseAcknowledged ack = new CloseAcknowledged("ext-order-1", "corr-1");
            when(cap.executeClose(request)).thenReturn(ack);

            ExecuteCloseApiRequest apiRequest = new ExecuteCloseApiRequest(brokerAccountId, "scope-1", "idem-1");
            BrokerPrincipal principal = new BrokerPrincipal(ownerId, "user", "TRADER");

            ResponseEntity<BrokerCloseApiResponse> response = controller.executeClose(apiRequest, principal);

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            BrokerCloseApiResponse body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.outcome()).isEqualTo("ACKNOWLEDGED");
            assertThat(body.externalOrderId()).isEqualTo("ext-order-1");
            assertThat(body.correlationId()).isEqualTo("corr-1");
        }

        @Test
        void executeCloseReturnsOkWithRejectedResponse() {
            allowOwnership(brokerAccountId, ownerId);
            setupProviderWithCapability();
            PositionManagementCapability cap = provider.capability(PositionManagementCapability.class).orElseThrow();

            ExecuteCloseRequest request = new ExecuteCloseRequest(brokerAccountId, "scope-1", "idem-1");
            CloseRejected rej = new CloseRejected("ext-order-1", "INSUFFICIENT_FUNDS");
            when(cap.executeClose(request)).thenReturn(rej);

            ExecuteCloseApiRequest apiRequest = new ExecuteCloseApiRequest(brokerAccountId, "scope-1", "idem-1");
            BrokerPrincipal principal = new BrokerPrincipal(ownerId, "user", "TRADER");

            ResponseEntity<BrokerCloseApiResponse> response = controller.executeClose(apiRequest, principal);

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            BrokerCloseApiResponse body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.outcome()).isEqualTo("REJECTED");
            assertThat(body.reasonCode()).isEqualTo("INSUFFICIENT_FUNDS");
        }

        @Test
        void executeCloseReturnsOkWithUnknownResponse() {
            allowOwnership(brokerAccountId, ownerId);
            setupProviderWithCapability();
            PositionManagementCapability cap = provider.capability(PositionManagementCapability.class).orElseThrow();

            ExecuteCloseRequest request = new ExecuteCloseRequest(brokerAccountId, "scope-1", "idem-1");
            CloseUnknown unk = new CloseUnknown("TIMEOUT");
            when(cap.executeClose(request)).thenReturn(unk);

            ExecuteCloseApiRequest apiRequest = new ExecuteCloseApiRequest(brokerAccountId, "scope-1", "idem-1");
            BrokerPrincipal principal = new BrokerPrincipal(ownerId, "user", "TRADER");

            ResponseEntity<BrokerCloseApiResponse> response = controller.executeClose(apiRequest, principal);

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            BrokerCloseApiResponse body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.outcome()).isEqualTo("UNKNOWN");
            assertThat(body.reasonCode()).isEqualTo("TIMEOUT");
        }

        @Test
        void reconcileCloseReturnsOkWithExposureAbsentResponse() {
            allowOwnership(brokerAccountId, ownerId);
            setupProviderWithCapability();
            PositionManagementCapability cap = provider.capability(PositionManagementCapability.class).orElseThrow();

            ReconcileCloseRequest request = new ReconcileCloseRequest(brokerAccountId, "scope-1", "idem-1");
            ExposureConfirmedAbsent absent = new ExposureConfirmedAbsent();
            when(cap.reconcile(request)).thenReturn(absent);

            ReconcileCloseApiRequest apiRequest = new ReconcileCloseApiRequest(brokerAccountId, "scope-1", "idem-1");
            BrokerPrincipal principal = new BrokerPrincipal(ownerId, "user", "TRADER");

            ResponseEntity<ReconcileCloseApiResponse> response = controller.reconcileClose(apiRequest, principal);

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            ReconcileCloseApiResponse body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.outcome()).isEqualTo("EXPOSURE_CONFIRMED_ABSENT");
        }

        @Test
        void reconcileCloseReturnsOkWithCommandNotExecutedResponse() {
            allowOwnership(brokerAccountId, ownerId);
            setupProviderWithCapability();
            PositionManagementCapability cap = provider.capability(PositionManagementCapability.class).orElseThrow();

            ReconcileCloseRequest request = new ReconcileCloseRequest(brokerAccountId, "scope-1", "idem-1");
            CommandConfirmedNotExecuted notExec = new CommandConfirmedNotExecuted();
            when(cap.reconcile(request)).thenReturn(notExec);

            ReconcileCloseApiRequest apiRequest = new ReconcileCloseApiRequest(brokerAccountId, "scope-1", "idem-1");
            BrokerPrincipal principal = new BrokerPrincipal(ownerId, "user", "TRADER");

            ResponseEntity<ReconcileCloseApiResponse> response = controller.reconcileClose(apiRequest, principal);

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            ReconcileCloseApiResponse body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.outcome()).isEqualTo("COMMAND_CONFIRMED_NOT_EXECUTED");
        }

        @Test
        void reconcileCloseReturnsOkWithInconclusiveResponse() {
            allowOwnership(brokerAccountId, ownerId);
            setupProviderWithCapability();
            PositionManagementCapability cap = provider.capability(PositionManagementCapability.class).orElseThrow();

            ReconcileCloseRequest request = new ReconcileCloseRequest(brokerAccountId, "scope-1", "idem-1");
            Inconclusive inconclusive = new Inconclusive("UNKNOWN_ERROR");
            when(cap.reconcile(request)).thenReturn(inconclusive);

            ReconcileCloseApiRequest apiRequest = new ReconcileCloseApiRequest(brokerAccountId, "scope-1", "idem-1");
            BrokerPrincipal principal = new BrokerPrincipal(ownerId, "user", "TRADER");

            ResponseEntity<ReconcileCloseApiResponse> response = controller.reconcileClose(apiRequest, principal);

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            ReconcileCloseApiResponse body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.outcome()).isEqualTo("INCONCLUSIVE");
            assertThat(body.reconciliationResult()).isEqualTo("INCONCLUSIVE: UNKNOWN_ERROR");
        }
    }
}
