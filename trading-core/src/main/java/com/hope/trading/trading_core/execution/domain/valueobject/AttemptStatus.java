package com.hope.trading.trading_core.execution.domain.valueobject;

public enum AttemptStatus {
    CREATED, STARTED, SUCCEEDED, FAILED, TIMED_OUT, OUTCOME_UNKNOWN, RECONCILED;
    public boolean active() { return this == CREATED || this == STARTED; }
}
