package com.hope.trading.trading_core.positionclose.infrastructure.persistence;

import com.hope.trading.trading_core.positionclose.domain.model.PositionCloseCommand;
import com.hope.trading.trading_core.positionclose.domain.model.PositionCloseStatus;
import com.hope.trading.trading_core.positionclose.domain.model.ReconciliationCloseResult;

public final class PositionCloseCommandMapper {
    private PositionCloseCommandMapper() {}

    public static PositionCloseCommand toDomain(PositionCloseCommandEntity entity) {
        if (entity == null) return null;
        return new PositionCloseCommand(
                entity.id,
                entity.accountId,
                entity.brokerAccountId,
                entity.brokerPositionReference,
                entity.resolvedMutationScope,
                entity.idempotencyKey,
                entity.status,
                entity.reconciliationResult,
                entity.externalOrderId,
                entity.failureReason,
                entity.createdAt,
                entity.updatedAt,
                entity.version
        );
    }

    public static PositionCloseCommandEntity toEntity(PositionCloseCommand domain) {
        if (domain == null) return null;
        PositionCloseCommandEntity entity = new PositionCloseCommandEntity();
        entity.id = domain.id;
        entity.accountId = domain.accountId;
        entity.brokerAccountId = domain.brokerAccountId;
        entity.brokerPositionReference = domain.brokerPositionReference;
        entity.resolvedMutationScope = domain.resolvedMutationScope;
        entity.idempotencyKey = domain.idempotencyKey;
        entity.status = domain.status;
        entity.reconciliationResult = domain.reconciliationResult;
        entity.externalOrderId = domain.externalOrderId;
        entity.failureReason = domain.failureReason;
        entity.createdAt = domain.createdAt;
        entity.updatedAt = domain.updatedAt;
        entity.version = domain.version;
        return entity;
    }
}