package com.hope.trading.trading_core.positionclose.domain.model;

import java.time.Instant;
import java.util.UUID;

public final class PositionCloseCommand {
    public final UUID id;
    public final UUID accountId;
    public final UUID brokerAccountId;
    public final String brokerPositionReference;
    public final String resolvedMutationScope;
    public final String idempotencyKey;
    public final PositionCloseStatus status;
    public final ReconciliationCloseResult reconciliationResult;
    public final String externalOrderId;
    public final String failureReason;
    public final Instant createdAt;
    public final Instant updatedAt;
    public final long version;

    public PositionCloseCommand(UUID id, UUID accountId, UUID brokerAccountId, String brokerPositionReference,
            String resolvedMutationScope, String idempotencyKey, PositionCloseStatus status,
            ReconciliationCloseResult reconciliationResult, String externalOrderId, String failureReason,
            Instant createdAt, Instant updatedAt, long version) {
        this.id = id;
        this.accountId = accountId;
        this.brokerAccountId = brokerAccountId;
        this.brokerPositionReference = brokerPositionReference;
        this.resolvedMutationScope = resolvedMutationScope;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.reconciliationResult = reconciliationResult;
        this.externalOrderId = externalOrderId;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public boolean isActive() {
        return status == PositionCloseStatus.CREATED || status == PositionCloseStatus.SUBMITTED
                || status == PositionCloseStatus.ACKNOWLEDGED || status == PositionCloseStatus.UNKNOWN;
    }

    public boolean isTerminal() {
        return !isActive();
    }

    public boolean isReconcilable() {
        return status == PositionCloseStatus.ACKNOWLEDGED || status == PositionCloseStatus.UNKNOWN;
    }
}