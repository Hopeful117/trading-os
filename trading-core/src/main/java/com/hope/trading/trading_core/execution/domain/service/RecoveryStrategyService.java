package com.hope.trading.trading_core.execution.domain.service;

import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionStatus;

public final class RecoveryStrategyService {
    public RecoveryStrategy determine(ExecutionIntent intent) {
        return switch (intent.status()) {
            case SUBMISSION_OUTCOME_UNKNOWN, SUBMISSION_IN_PROGRESS ->
                    RecoveryStrategy.RECONCILE;
            case RECONCILIATION_IN_PROGRESS, RECOVERY_BLOCKED ->
                    RecoveryStrategy.RESUME_RECONCILIATION;
            case COMPLETED, CANCELLED, EXPIRED -> RecoveryStrategy.IGNORE;
            default -> RecoveryStrategy.NONE;
        };
    }
    public enum RecoveryStrategy {
        RECONCILE, RESUME_RECONCILIATION, IGNORE, NONE
    }
}
