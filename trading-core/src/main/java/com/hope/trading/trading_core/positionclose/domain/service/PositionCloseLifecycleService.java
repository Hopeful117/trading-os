package com.hope.trading.trading_core.positionclose.domain.service;

import com.hope.trading.trading_core.positionclose.domain.model.PositionCloseCommand;
import com.hope.trading.trading_core.positionclose.domain.model.PositionCloseStatus;
import com.hope.trading.trading_core.positionclose.domain.model.ReconciliationCloseResult;
import java.time.Instant;
import java.util.UUID;

public final class PositionCloseLifecycleService {
    private PositionCloseLifecycleService() {}

    public static PositionCloseCommand create(UUID id, UUID accountId, UUID brokerAccountId,
            String brokerPositionReference, String resolvedMutationScope, String idempotencyKey,
            Instant now) {
        return new PositionCloseCommand(id, accountId, brokerAccountId, brokerPositionReference,
                resolvedMutationScope, idempotencyKey, PositionCloseStatus.CREATED, null, null, null,
                now, now, 0);
    }

    public static PositionCloseCommand transitionToSubmitted(PositionCloseCommand command, Instant now) {
        requireState(command, PositionCloseStatus.CREATED);
        return withStatus(command, PositionCloseStatus.SUBMITTED, now);
    }

    public static PositionCloseCommand transitionToAcknowledged(PositionCloseCommand command,
            String externalOrderId, Instant now) {
        requireState(command, PositionCloseStatus.SUBMITTED);
        return withStatusAndExternalId(command, PositionCloseStatus.ACKNOWLEDGED, externalOrderId, now);
    }

    public static PositionCloseCommand transitionToRejected(PositionCloseCommand command,
            String failureReason, Instant now) {
        requireState(command, PositionCloseStatus.SUBMITTED);
        return withFailureReason(command, PositionCloseStatus.REJECTED, failureReason, now);
    }

    public static PositionCloseCommand transitionToUnknown(PositionCloseCommand command,
            String failureReason, Instant now) {
        requireState(command, PositionCloseStatus.SUBMITTED);
        return withFailureReason(command, PositionCloseStatus.UNKNOWN, failureReason, now);
    }

    public static PositionCloseCommand transitionToNotSubmitted(PositionCloseCommand command, Instant now) {
        requireState(command, PositionCloseStatus.CREATED);
        return withStatus(command, PositionCloseStatus.NOT_SUBMITTED, now);
    }

    public static PositionCloseCommand reconcile(PositionCloseCommand command,
            ReconciliationCloseResult result, Instant now) {
        if (!command.isReconcilable()) {
            throw new IllegalStateException("Command not in reconcilable state: " + command.status);
        }
        PositionCloseStatus newStatus;
        if (result == ReconciliationCloseResult.EXPOSURE_CONFIRMED_ABSENT) {
            newStatus = PositionCloseStatus.CLOSED;
        } else {
            newStatus = command.status;
        }
        return withReconciliationResult(command, newStatus, result, now);
    }

    private static void requireState(PositionCloseCommand command, PositionCloseStatus expected) {
        if (command.status != expected) {
            throw new IllegalStateException("Invalid state transition: expected " + expected + " but was " + command.status);
        }
    }

    private static PositionCloseCommand withStatus(PositionCloseCommand command, PositionCloseStatus status, Instant now) {
        return new PositionCloseCommand(command.id, command.accountId, command.brokerAccountId,
                command.brokerPositionReference, command.resolvedMutationScope, command.idempotencyKey,
                status, command.reconciliationResult, command.externalOrderId, command.failureReason,
                command.createdAt, now, command.version + 1);
    }

    private static PositionCloseCommand withStatusAndExternalId(PositionCloseCommand command,
            PositionCloseStatus status, String externalOrderId, Instant now) {
        return new PositionCloseCommand(command.id, command.accountId, command.brokerAccountId,
                command.brokerPositionReference, command.resolvedMutationScope, command.idempotencyKey,
                status, command.reconciliationResult, externalOrderId, command.failureReason,
                command.createdAt, now, command.version + 1);
    }

    private static PositionCloseCommand withFailureReason(PositionCloseCommand command,
            PositionCloseStatus status, String failureReason, Instant now) {
        return new PositionCloseCommand(command.id, command.accountId, command.brokerAccountId,
                command.brokerPositionReference, command.resolvedMutationScope, command.idempotencyKey,
                status, command.reconciliationResult, command.externalOrderId, failureReason,
                command.createdAt, now, command.version + 1);
    }

    private static PositionCloseCommand withReconciliationResult(PositionCloseCommand command,
            PositionCloseStatus status, ReconciliationCloseResult result, Instant now) {
        return new PositionCloseCommand(command.id, command.accountId, command.brokerAccountId,
                command.brokerPositionReference, command.resolvedMutationScope, command.idempotencyKey,
                status, result, command.externalOrderId, command.failureReason,
                command.createdAt, now, command.version + 1);
    }
}