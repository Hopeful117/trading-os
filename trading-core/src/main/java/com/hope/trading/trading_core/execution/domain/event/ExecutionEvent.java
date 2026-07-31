package com.hope.trading.trading_core.execution.domain.event;

import com.hope.trading.trading_core.execution.domain.valueobject.*;
import java.time.Instant;

public sealed interface ExecutionEvent permits
        ExecutionEvent.ExecutionIntentCreated, ExecutionEvent.ExecutionIntentValidated,
        ExecutionEvent.ExecutionAttemptCreated, ExecutionEvent.ExecutionAttemptStarted,
        ExecutionEvent.ExecutionAttemptSucceeded, ExecutionEvent.ExecutionAttemptFailed,
        ExecutionEvent.ExecutionAttemptTimedOut, ExecutionEvent.ExecutionAttemptUnknown,
        ExecutionEvent.BrokerSubmissionStarted,
        ExecutionEvent.BrokerSubmissionAcknowledged, ExecutionEvent.BrokerOrderLinked,
        ExecutionEvent.BrokerOrderRejected, ExecutionEvent.BrokerOrderCancelled,
        ExecutionEvent.BrokerOrderFilled, ExecutionEvent.BrokerOrderPartiallyFilled,
        ExecutionEvent.ExecutionRetryScheduled, ExecutionEvent.ExecutionRetryAborted,
        ExecutionEvent.ExecutionRecoveryStarted, ExecutionEvent.ExecutionRecoveryCompleted,
        ExecutionEvent.ExecutionRecoveryBlocked, ExecutionEvent.ExecutionIntentCancelled {
    ExecutionIntentId intentId();
    Instant occurredAt();

    record ExecutionIntentCreated(ExecutionIntentId intentId, Instant occurredAt)
            implements ExecutionEvent {}
    record ExecutionIntentValidated(ExecutionIntentId intentId, Instant occurredAt)
            implements ExecutionEvent {}
    record ExecutionAttemptCreated(ExecutionIntentId intentId, ExecutionAttemptId attemptId,
                                   int attemptNumber, Instant occurredAt) implements ExecutionEvent {}
    record ExecutionAttemptStarted(ExecutionIntentId intentId, ExecutionAttemptId attemptId,
                                   Instant occurredAt) implements ExecutionEvent {}
    record ExecutionAttemptSucceeded(ExecutionIntentId intentId, ExecutionAttemptId attemptId,
                                     Instant occurredAt) implements ExecutionEvent {}
    record ExecutionAttemptFailed(ExecutionIntentId intentId, ExecutionAttemptId attemptId,
                                  String reasonCode, Instant occurredAt) implements ExecutionEvent {}
    record ExecutionAttemptTimedOut(ExecutionIntentId intentId, ExecutionAttemptId attemptId,
                                    Instant occurredAt) implements ExecutionEvent {}
    record ExecutionAttemptUnknown(ExecutionIntentId intentId, ExecutionAttemptId attemptId,
                                   Instant occurredAt) implements ExecutionEvent {}
    record BrokerSubmissionStarted(ExecutionIntentId intentId, ExecutionAttemptId attemptId,
                                   Instant occurredAt) implements ExecutionEvent {}
    record BrokerSubmissionAcknowledged(ExecutionIntentId intentId, ExecutionAttemptId attemptId,
                                        String brokerReference, Instant occurredAt)
            implements ExecutionEvent {}
    record BrokerOrderLinked(ExecutionIntentId intentId, BrokerOrderId orderId,
                             Instant occurredAt) implements ExecutionEvent {}
    record BrokerOrderRejected(ExecutionIntentId intentId, BrokerOrderId orderId,
                               String reasonCode, Instant occurredAt) implements ExecutionEvent {}
    record BrokerOrderCancelled(ExecutionIntentId intentId, BrokerOrderId orderId,
                                Instant occurredAt) implements ExecutionEvent {}
    record BrokerOrderFilled(ExecutionIntentId intentId, BrokerOrderId orderId,
                             Instant occurredAt) implements ExecutionEvent {}
    record BrokerOrderPartiallyFilled(ExecutionIntentId intentId, BrokerOrderId orderId,
                                      Instant occurredAt) implements ExecutionEvent {}
    record ExecutionRetryScheduled(ExecutionIntentId intentId, int attemptNumber,
                                   Instant occurredAt) implements ExecutionEvent {}
    record ExecutionRetryAborted(ExecutionIntentId intentId, String reasonCode,
                                 Instant occurredAt) implements ExecutionEvent {}
    record ExecutionRecoveryStarted(ExecutionIntentId intentId, Instant occurredAt)
            implements ExecutionEvent {}
    record ExecutionRecoveryCompleted(ExecutionIntentId intentId, Instant occurredAt)
            implements ExecutionEvent {}
    record ExecutionRecoveryBlocked(ExecutionIntentId intentId, String reasonCode,
                                    Instant occurredAt) implements ExecutionEvent {}
    record ExecutionIntentCancelled(ExecutionIntentId intentId, Instant occurredAt)
            implements ExecutionEvent {}
}
