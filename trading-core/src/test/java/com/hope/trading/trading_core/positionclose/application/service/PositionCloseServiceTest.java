package com.hope.trading.trading_core.positionclose.application.service;

import com.hope.trading.trading_core.positionclose.application.port.BrokerPositionClosePort;
import com.hope.trading.trading_core.positionclose.domain.model.PositionCloseCommand;
import com.hope.trading.trading_core.positionclose.domain.model.PositionCloseStatus;
import com.hope.trading.trading_core.positionclose.domain.repository.PositionCloseCommandRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PositionCloseServiceTest {

    @Mock
    private PositionCloseCommandRepositoryPort repository;
    @Mock
    private BrokerPositionClosePort brokerPort;

    private PositionCloseService service;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID BROKER_ACCOUNT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PositionCloseService(repository, brokerPort);
    }

    @Test
    void closeReturnsExistingCommandForSameIdempotencyKey() {
        PositionCloseCommand existing = new PositionCloseCommand(
                UUID.randomUUID(), USER_ID, ACCOUNT_ID, "ref", "scope", "key-1",
                PositionCloseStatus.SUBMITTED, null, null, null,
                Instant.now(), Instant.now(), 0);
        when(repository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        PositionCloseCommand result = service.close(USER_ID, ACCOUNT_ID, "ref", "key-1");
        assertSame(existing, result);
        verifyNoInteractions(brokerPort);
    }

    @Test
    void closeResolvesAndSubmitsSuccessfully() {
        when(repository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(brokerPort.resolveTarget(ACCOUNT_ID, "ref"))
                .thenReturn(new BrokerPositionClosePort.ResolveTargetResponse(BROKER_ACCOUNT_ID, "scope-1"));
        when(repository.findActiveByBrokerAccountAndScope(BROKER_ACCOUNT_ID, "scope-1"))
                .thenReturn(List.of());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(brokerPort.executeClose("scope-1", "key-1"))
                .thenReturn(new BrokerPositionClosePort.BrokerCloseResponse("ACKNOWLEDGED", "ext-123", "corr-1", "ACKNOWLEDGED", null));

        PositionCloseCommand result = service.close(USER_ID, ACCOUNT_ID, "ref", "key-1");
        assertEquals(PositionCloseStatus.ACKNOWLEDGED, result.status);
        assertEquals("ext-123", result.externalOrderId);
        verify(repository, times(3)).save(any());
    }

    @Test
    void closeTransitionsToUnknownOnBrokerFailure() {
        when(repository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(brokerPort.resolveTarget(ACCOUNT_ID, "ref"))
                .thenReturn(new BrokerPositionClosePort.ResolveTargetResponse(BROKER_ACCOUNT_ID, "scope-1"));
        when(repository.findActiveByBrokerAccountAndScope(BROKER_ACCOUNT_ID, "scope-1"))
                .thenReturn(List.of());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(brokerPort.executeClose("scope-1", "key-1"))
                .thenThrow(new RuntimeException("Connection refused"));

        PositionCloseCommand result = service.close(USER_ID, ACCOUNT_ID, "ref", "key-1");
        assertEquals(PositionCloseStatus.UNKNOWN, result.status);
        assertEquals("RUNTIME", result.failureReason);
    }

    @Test
    void closeRejectsWhenActiveCommandExistsForScope() {
        when(repository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(brokerPort.resolveTarget(ACCOUNT_ID, "ref"))
                .thenReturn(new BrokerPositionClosePort.ResolveTargetResponse(BROKER_ACCOUNT_ID, "scope-1"));
        PositionCloseCommand activeCmd = new PositionCloseCommand(
                UUID.randomUUID(), USER_ID, BROKER_ACCOUNT_ID, "ref", "scope-1", "key-0",
                PositionCloseStatus.SUBMITTED, null, null, null,
                Instant.now(), Instant.now(), 0);
        when(repository.findActiveByBrokerAccountAndScope(BROKER_ACCOUNT_ID, "scope-1"))
                .thenReturn(List.of(activeCmd));

        assertThrows(PositionCloseConflictException.class, () ->
                service.close(USER_ID, ACCOUNT_ID, "ref", "key-1"));
    }

    @Test
    void closeHandlesRejectedOutcome() {
        when(repository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(brokerPort.resolveTarget(ACCOUNT_ID, "ref"))
                .thenReturn(new BrokerPositionClosePort.ResolveTargetResponse(BROKER_ACCOUNT_ID, "scope-1"));
        when(repository.findActiveByBrokerAccountAndScope(BROKER_ACCOUNT_ID, "scope-1"))
                .thenReturn(List.of());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(brokerPort.executeClose("scope-1", "key-1"))
                .thenReturn(new BrokerPositionClosePort.BrokerCloseResponse("REJECTED", null, null, "REJECTED", "INSUFFICIENT_FUNDS"));

        PositionCloseCommand result = service.close(USER_ID, ACCOUNT_ID, "ref", "key-1");
        assertEquals(PositionCloseStatus.REJECTED, result.status);
        assertEquals("INSUFFICIENT_FUNDS", result.failureReason);
    }

    @Test
    void reconcileSuccessfulExposureAbsent() {
        PositionCloseCommand cmd = new PositionCloseCommand(
                UUID.randomUUID(), USER_ID, BROKER_ACCOUNT_ID, "ref", "scope-1", "key-1",
                PositionCloseStatus.ACKNOWLEDGED, null, "ext-1", null,
                Instant.now(), Instant.now(), 0);
        when(repository.findById(cmd.id)).thenReturn(Optional.of(cmd));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(brokerPort.reconcileClose("scope-1", "key-1"))
                .thenReturn(new BrokerPositionClosePort.BrokerReconcileResponse("EXPOSURE_CONFIRMED_ABSENT", "EXPOSURE_CONFIRMED_ABSENT"));

        PositionCloseCommand result = service.reconcile(USER_ID, cmd.id);
        assertEquals(PositionCloseStatus.CLOSED, result.status);
    }

    @Test
    void reconcileRejectsUnauthorizedUser() {
        PositionCloseCommand cmd = new PositionCloseCommand(
                UUID.randomUUID(), UUID.randomUUID(), BROKER_ACCOUNT_ID, "ref", "scope-1", "key-1",
                PositionCloseStatus.ACKNOWLEDGED, null, "ext-1", null,
                Instant.now(), Instant.now(), 0);
        when(repository.findById(cmd.id)).thenReturn(Optional.of(cmd));

        assertThrows(PositionCloseAuthorizationException.class, () ->
                service.reconcile(USER_ID, cmd.id));
    }

    @Test
    void reconcileRejectsNonReconcilableCommand() {
        PositionCloseCommand cmd = new PositionCloseCommand(
                UUID.randomUUID(), USER_ID, BROKER_ACCOUNT_ID, "ref", "scope-1", "key-1",
                PositionCloseStatus.SUBMITTED, null, null, null,
                Instant.now(), Instant.now(), 0);
        when(repository.findById(cmd.id)).thenReturn(Optional.of(cmd));

        assertThrows(PositionCloseConflictException.class, () ->
                service.reconcile(USER_ID, cmd.id));
    }

    @Test
    void reconcileCommandNotFound() {
        UUID cmdId = UUID.randomUUID();
        when(repository.findById(cmdId)).thenReturn(Optional.empty());
        assertThrows(PositionCloseCommandNotFoundException.class, () ->
                service.reconcile(USER_ID, cmdId));
    }
}
