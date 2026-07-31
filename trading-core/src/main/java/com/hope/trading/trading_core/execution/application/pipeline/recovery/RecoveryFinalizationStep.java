package com.hope.trading.trading_core.execution.application.pipeline.recovery;

import com.hope.trading.trading_core.execution.application.port.*;
import com.hope.trading.trading_core.execution.domain.aggregate.*;
import com.hope.trading.trading_core.execution.domain.event.ExecutionEvent;
import com.hope.trading.trading_core.execution.domain.repository.*;
import com.hope.trading.trading_core.execution.domain.service.RecoveryStrategyService.RecoveryStrategy;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import java.util.Objects;

public final class RecoveryFinalizationStep {
    private final ExecutionIntentRepositoryPort intents;
    private final ExecutionAttemptRepositoryPort attempts;
    private final BrokerOrderRepositoryPort orders;
    private final ExecutionIdGenerator ids;
    public RecoveryFinalizationStep(ExecutionIntentRepositoryPort intents,
            ExecutionAttemptRepositoryPort attempts, BrokerOrderRepositoryPort orders,
            ExecutionIdGenerator ids) {
        this.intents = Objects.requireNonNull(intents); this.attempts = Objects.requireNonNull(attempts);
        this.orders = Objects.requireNonNull(orders); this.ids = Objects.requireNonNull(ids);
    }
    public void execute(RecoveryPipelineContext context) {
        if (context.strategy() == RecoveryStrategy.IGNORE
                || context.strategy() == RecoveryStrategy.NONE) return;
        ExecutionIntent intent = context.intent();
        if (intent.status() != ExecutionStatus.RECONCILIATION_IN_PROGRESS) {
            intent.transition(ExecutionStatus.RECONCILIATION_IN_PROGRESS, context.now());
        }
        normalizeAttempt(context);
        switch (context.reconciliation()) {
            case BrokerExecutionPort.ReconciledOrder result -> {
                context.attempt().reconcile(result.correlationId(), "ORDER_FOUND", context.now());
                clearActive(intent, context);
                BrokerOrder order = BrokerOrder.rehydrate(ids.nextBrokerOrderId(),
                        intent.id(), context.attempt().id(), result.externalOrderId(),
                        result.status(), java.util.List.of(), context.now(), context.now(), 0);
                orders.save(order); intent.transition(ExecutionStatus.COMPLETED, context.now());
                intent.addEvent(new ExecutionEvent.BrokerOrderLinked(
                        intent.id(), order.id(), context.now()));
                intent.addEvent(new ExecutionEvent.ExecutionRecoveryCompleted(
                        intent.id(), context.now()));
            }
            case BrokerExecutionPort.ConfirmedAbsent ignored -> {
                context.attempt().reconcile(null, "ORDER_CONFIRMED_ABSENT", context.now());
                clearActive(intent, context);
                intent.transition(ExecutionStatus.VALIDATED, context.now());
                intent.addEvent(new ExecutionEvent.ExecutionRecoveryCompleted(
                        intent.id(), context.now()));
            }
            case BrokerExecutionPort.Inconsistent inconsistent -> {
                intent.transition(ExecutionStatus.RECOVERY_BLOCKED, context.now());
                intent.addEvent(new ExecutionEvent.ExecutionRecoveryBlocked(
                        intent.id(), inconsistent.reasonCode(), context.now()));
            }
        }
        attempts.save(context.attempt()); intents.save(intent);
    }
    private void normalizeAttempt(RecoveryPipelineContext context) {
        if (context.attempt().status() == AttemptStatus.STARTED) {
            context.attempt().markUnknown(context.now());
        } else if (context.attempt().status() == AttemptStatus.TIMED_OUT) {
            context.attempt().markUnknown(context.now());
        }
    }
    private void clearActive(ExecutionIntent intent, RecoveryPipelineContext context) {
        if (intent.activeAttemptId().isPresent()) {
            intent.clearActiveAttempt(context.attempt().id(), context.now());
        }
    }
}
