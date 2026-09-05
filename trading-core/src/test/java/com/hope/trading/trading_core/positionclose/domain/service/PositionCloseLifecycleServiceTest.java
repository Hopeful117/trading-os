package com.hope.trading.trading_core.positionclose.domain.service;

import com.hope.trading.trading_core.positionclose.domain.model.PositionCloseCommand;
import com.hope.trading.trading_core.positionclose.domain.model.PositionCloseStatus;
import com.hope.trading.trading_core.positionclose.domain.model.ReconciliationCloseResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PositionCloseLifecycleServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-05T00:00:00Z");
    private static final UUID ID = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID BROKER_ACCOUNT_ID = UUID.randomUUID();

    @Test
    void createProducesCreatedStatus() {
        PositionCloseCommand cmd = PositionCloseLifecycleService.create(
                ID, ACCOUNT_ID, BROKER_ACCOUNT_ID, "ref", "scope", "key-1", NOW);
        assertEquals(PositionCloseStatus.CREATED, cmd.status);
        assertEquals(ID, cmd.id);
        assertEquals(ACCOUNT_ID, cmd.accountId);
        assertEquals(BROKER_ACCOUNT_ID, cmd.brokerAccountId);
        assertEquals("ref", cmd.brokerPositionReference);
        assertEquals("scope", cmd.resolvedMutationScope);
        assertEquals("key-1", cmd.idempotencyKey);
        assertEquals(0, cmd.version);
        assertNull(cmd.reconciliationResult);
        assertNull(cmd.externalOrderId);
        assertNull(cmd.failureReason);
    }

    @Test
    void transitionToSubmittedFromCreated() {
        PositionCloseCommand created = PositionCloseLifecycleService.create(
                ID, ACCOUNT_ID, BROKER_ACCOUNT_ID, "ref", "scope", "key-1", NOW);
        PositionCloseCommand submitted = PositionCloseLifecycleService.transitionToSubmitted(created, NOW.plusSeconds(1));
        assertEquals(PositionCloseStatus.SUBMITTED, submitted.status);
        assertEquals(1, submitted.version);
        assertEquals(NOW.plusSeconds(1), submitted.updatedAt);
    }

    @Test
    void transitionToSubmittedFromInvalidStateThrows() {
        PositionCloseCommand created = PositionCloseLifecycleService.create(
                ID, ACCOUNT_ID, BROKER_ACCOUNT_ID, "ref", "scope", "key-1", NOW);
        PositionCloseCommand submitted = PositionCloseLifecycleService.transitionToSubmitted(created, NOW);
        assertThrows(IllegalStateException.class, () ->
                PositionCloseLifecycleService.transitionToSubmitted(submitted, NOW));
    }

    @Test
    void transitionToAcknowledgedFromSubmitted() {
        PositionCloseCommand submitted = PositionCloseLifecycleService.transitionToSubmitted(
                PositionCloseLifecycleService.create(ID, ACCOUNT_ID, BROKER_ACCOUNT_ID, "ref", "scope", "key-1", NOW), NOW);
        PositionCloseCommand ack = PositionCloseLifecycleService.transitionToAcknowledged(submitted, "ext-123", NOW);
        assertEquals(PositionCloseStatus.ACKNOWLEDGED, ack.status);
        assertEquals("ext-123", ack.externalOrderId);
        assertEquals(2, ack.version);
    }

    @Test
    void transitionToRejectedFromSubmitted() {
        PositionCloseCommand submitted = PositionCloseLifecycleService.transitionToSubmitted(
                PositionCloseLifecycleService.create(ID, ACCOUNT_ID, BROKER_ACCOUNT_ID, "ref", "scope", "key-1", NOW), NOW);
        PositionCloseCommand rejected = PositionCloseLifecycleService.transitionToRejected(submitted, "INSUFFICIENT_FUNDS", NOW);
        assertEquals(PositionCloseStatus.REJECTED, rejected.status);
        assertEquals("INSUFFICIENT_FUNDS", rejected.failureReason);
    }

    @Test
    void transitionToUnknownFromSubmitted() {
        PositionCloseCommand submitted = PositionCloseLifecycleService.transitionToSubmitted(
                PositionCloseLifecycleService.create(ID, ACCOUNT_ID, BROKER_ACCOUNT_ID, "ref", "scope", "key-1", NOW), NOW);
        PositionCloseCommand unknown = PositionCloseLifecycleService.transitionToUnknown(submitted, "TIMEOUT", NOW);
        assertEquals(PositionCloseStatus.UNKNOWN, unknown.status);
        assertEquals("TIMEOUT", unknown.failureReason);
    }

    @Test
    void transitionToNotSubmittedFromCreated() {
        PositionCloseCommand created = PositionCloseLifecycleService.create(
                ID, ACCOUNT_ID, BROKER_ACCOUNT_ID, "ref", "scope", "key-1", NOW);
        PositionCloseCommand notSubmitted = PositionCloseLifecycleService.transitionToNotSubmitted(created, NOW);
        assertEquals(PositionCloseStatus.NOT_SUBMITTED, notSubmitted.status);
    }

    @Test
    void reconcileExposureConfirmedAbsentCloses() {
        PositionCloseCommand submitted = PositionCloseLifecycleService.transitionToSubmitted(
                PositionCloseLifecycleService.create(ID, ACCOUNT_ID, BROKER_ACCOUNT_ID, "ref", "scope", "key-1", NOW), NOW);
        PositionCloseCommand ack = PositionCloseLifecycleService.transitionToAcknowledged(submitted, "ext-123", NOW);
        PositionCloseCommand closed = PositionCloseLifecycleService.reconcile(ack, ReconciliationCloseResult.EXPOSURE_CONFIRMED_ABSENT, NOW);
        assertEquals(PositionCloseStatus.CLOSED, closed.status);
        assertEquals(ReconciliationCloseResult.EXPOSURE_CONFIRMED_ABSENT, closed.reconciliationResult);
    }

    @Test
    void reconcileCommandConfirmedNotExecutedStaysUnknown() {
        PositionCloseCommand submitted = PositionCloseLifecycleService.transitionToSubmitted(
                PositionCloseLifecycleService.create(ID, ACCOUNT_ID, BROKER_ACCOUNT_ID, "ref", "scope", "key-1", NOW), NOW);
        PositionCloseCommand unknown = PositionCloseLifecycleService.transitionToUnknown(submitted, "TIMEOUT", NOW);
        PositionCloseCommand result = PositionCloseLifecycleService.reconcile(unknown, ReconciliationCloseResult.COMMAND_CONFIRMED_NOT_EXECUTED, NOW);
        assertEquals(PositionCloseStatus.UNKNOWN, result.status);
        assertEquals(ReconciliationCloseResult.COMMAND_CONFIRMED_NOT_EXECUTED, result.reconciliationResult);
    }

    @Test
    void reconcileInconclusiveStaysCurrentStatus() {
        PositionCloseCommand submitted = PositionCloseLifecycleService.transitionToSubmitted(
                PositionCloseLifecycleService.create(ID, ACCOUNT_ID, BROKER_ACCOUNT_ID, "ref", "scope", "key-1", NOW), NOW);
        PositionCloseCommand ack = PositionCloseLifecycleService.transitionToAcknowledged(submitted, "ext-123", NOW);
        PositionCloseCommand result = PositionCloseLifecycleService.reconcile(ack, ReconciliationCloseResult.RECONCILIATION_INCONCLUSIVE, NOW);
        assertEquals(PositionCloseStatus.ACKNOWLEDGED, result.status);
        assertEquals(ReconciliationCloseResult.RECONCILIATION_INCONCLUSIVE, result.reconciliationResult);
    }

    @Test
    void reconcileOnNonReconcilableStateThrows() {
        PositionCloseCommand created = PositionCloseLifecycleService.create(
                ID, ACCOUNT_ID, BROKER_ACCOUNT_ID, "ref", "scope", "key-1", NOW);
        assertThrows(IllegalStateException.class, () ->
                PositionCloseLifecycleService.reconcile(created, ReconciliationCloseResult.EXPOSURE_CONFIRMED_ABSENT, NOW));
    }
}
