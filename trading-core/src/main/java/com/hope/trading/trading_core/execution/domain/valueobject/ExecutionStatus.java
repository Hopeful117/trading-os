package com.hope.trading.trading_core.execution.domain.valueobject;

public enum ExecutionStatus {
    CREATED, VALIDATED, SUBMISSION_IN_PROGRESS, SUBMISSION_OUTCOME_UNKNOWN,
    RECONCILIATION_IN_PROGRESS, COMPLETED, FAILED, RECOVERY_BLOCKED,
    CANCELLED, EXPIRED,
    RISK_REVALIDATION_REJECTED, RISK_REVALIDATION_UNAVAILABLE;

    public boolean terminal() {
        return this == COMPLETED || this == CANCELLED || this == EXPIRED || this == RISK_REVALIDATION_REJECTED;
    }
}
