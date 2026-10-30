package com.hope.trading.trading_core.positionclose.domain.model;

public enum ReconciliationCloseResult {
    EXPOSURE_CONFIRMED_ABSENT,
    EXPOSURE_CONFIRMED_PARTIAL,
    COMMAND_CONFIRMED_NOT_EXECUTED,
    RECONCILIATION_INCONCLUSIVE
}
