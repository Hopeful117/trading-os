package com.hope.trading.trading_core.positionclose.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PositionCloseCommandTest {

    private static final Instant NOW = Instant.parse("2026-09-05T00:00:00Z");

    private PositionCloseCommand cmd(PositionCloseStatus status) {
        return new PositionCloseCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "ref", "scope", "key", status, null, null, null,
                NOW, NOW, 0);
    }

    @Test
    void activeStatuses() {
        assertTrue(cmd(PositionCloseStatus.CREATED).isActive());
        assertTrue(cmd(PositionCloseStatus.SUBMITTED).isActive());
        assertTrue(cmd(PositionCloseStatus.ACKNOWLEDGED).isActive());
        assertTrue(cmd(PositionCloseStatus.UNKNOWN).isActive());
    }

    @Test
    void terminalStatuses() {
        assertTrue(cmd(PositionCloseStatus.REJECTED).isTerminal());
        assertTrue(cmd(PositionCloseStatus.CLOSED).isTerminal());
        assertTrue(cmd(PositionCloseStatus.NOT_SUBMITTED).isTerminal());
        assertFalse(cmd(PositionCloseStatus.CREATED).isTerminal());
    }

    @Test
    void reconcilableOnlyForAcknowledgedAndUnknown() {
        assertTrue(cmd(PositionCloseStatus.ACKNOWLEDGED).isReconcilable());
        assertTrue(cmd(PositionCloseStatus.UNKNOWN).isReconcilable());
        assertFalse(cmd(PositionCloseStatus.CREATED).isReconcilable());
        assertFalse(cmd(PositionCloseStatus.SUBMITTED).isReconcilable());
        assertFalse(cmd(PositionCloseStatus.REJECTED).isReconcilable());
        assertFalse(cmd(PositionCloseStatus.CLOSED).isReconcilable());
        assertFalse(cmd(PositionCloseStatus.NOT_SUBMITTED).isReconcilable());
    }

    @Test
    void preservesAllFields() {
        UUID id = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID brokerAccountId = UUID.randomUUID();
        PositionCloseCommand cmd = new PositionCloseCommand(
                id, accountId, brokerAccountId, "ref", "scope", "key",
                PositionCloseStatus.ACKNOWLEDGED, ReconciliationCloseResult.EXPOSURE_CONFIRMED_ABSENT,
                "ext-1", "reason", NOW, NOW.plusSeconds(10), 5);
        assertEquals(id, cmd.id);
        assertEquals(accountId, cmd.accountId);
        assertEquals(brokerAccountId, cmd.brokerAccountId);
        assertEquals("ref", cmd.brokerPositionReference);
        assertEquals("scope", cmd.resolvedMutationScope);
        assertEquals("key", cmd.idempotencyKey);
        assertEquals(PositionCloseStatus.ACKNOWLEDGED, cmd.status);
        assertEquals(ReconciliationCloseResult.EXPOSURE_CONFIRMED_ABSENT, cmd.reconciliationResult);
        assertEquals("ext-1", cmd.externalOrderId);
        assertEquals("reason", cmd.failureReason);
        assertEquals(NOW, cmd.createdAt);
        assertEquals(NOW.plusSeconds(10), cmd.updatedAt);
        assertEquals(5, cmd.version);
    }
}
