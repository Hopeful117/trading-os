package com.hope.trading.trading_core.execution.application.pipeline.recovery;

import com.hope.trading.trading_core.execution.domain.exception.ExecutionRecoveryException;
import com.hope.trading.trading_core.execution.domain.repository.ExecutionAttemptRepositoryPort;
import java.util.Objects;

public final class ExecutionInspectionStep {
    private final ExecutionAttemptRepositoryPort attempts;
    public ExecutionInspectionStep(ExecutionAttemptRepositoryPort attempts) {
        this.attempts = Objects.requireNonNull(attempts);
    }
    public void execute(RecoveryPipelineContext context) {
        context.attempt(attempts.findLatestByIntentId(context.intent().id())
                .orElseThrow(() -> new ExecutionRecoveryException(
                        "Recoverable execution has no submission attempt")));
    }
}
