package com.hope.trading.trading_core.execution.application.port;

import com.hope.trading.trading_core.execution.domain.model.ExecutionParameters;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import java.util.Objects;
import java.util.UUID;

public interface BrokerExecutionPort {
    SubmissionResult submit(ExecutionRequest request);
    void cancel(UUID brokerAccountId, String externalOrderId);
    ReconciliationResult reconcile(ReconciliationRequest request);

    record ExecutionRequest(
            ExecutionIntentId intentId, ExecutionAttemptId attemptId,
            IdempotencyKey idempotencyKey, UUID brokerAccountId,
            ExecutionParameters parameters
    ) {}
    sealed interface SubmissionResult permits Acknowledged, Rejected, Unknown {}
    record Acknowledged(String externalOrderId, String correlationId)
            implements SubmissionResult {
        public Acknowledged {
            Objects.requireNonNull(externalOrderId); Objects.requireNonNull(correlationId);
        }
    }
    record Rejected(String externalOrderId, String reasonCode)
            implements SubmissionResult {}
    record Unknown(String reasonCode) implements SubmissionResult {}
    record ReconciliationRequest(ExecutionIntentId intentId, ExecutionAttemptId attemptId,
                                 IdempotencyKey idempotencyKey, UUID brokerAccountId) {}
    sealed interface ReconciliationResult
            permits ReconciledOrder, ConfirmedAbsent, Inconsistent {}
    record ReconciledOrder(String externalOrderId, String correlationId,
                           BrokerOrderStatus status) implements ReconciliationResult {}
    record ConfirmedAbsent() implements ReconciliationResult {}
    record Inconsistent(String reasonCode) implements ReconciliationResult {}
}
