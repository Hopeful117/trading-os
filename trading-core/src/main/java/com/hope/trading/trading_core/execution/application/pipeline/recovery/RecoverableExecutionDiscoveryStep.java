package com.hope.trading.trading_core.execution.application.pipeline.recovery;

import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.repository.ExecutionIntentRepositoryPort;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionStatus;
import java.util.*;

public final class RecoverableExecutionDiscoveryStep {
    private static final Set<ExecutionStatus> RECOVERABLE = Set.of(
            ExecutionStatus.SUBMISSION_IN_PROGRESS,
            ExecutionStatus.SUBMISSION_OUTCOME_UNKNOWN,
            ExecutionStatus.RECONCILIATION_IN_PROGRESS,
            ExecutionStatus.RECOVERY_BLOCKED);
    private final ExecutionIntentRepositoryPort intents;
    public RecoverableExecutionDiscoveryStep(ExecutionIntentRepositoryPort intents) {
        this.intents = Objects.requireNonNull(intents);
    }
    public List<ExecutionIntent> execute() {
        return List.copyOf(intents.findByStatuses(RECOVERABLE));
    }
}
