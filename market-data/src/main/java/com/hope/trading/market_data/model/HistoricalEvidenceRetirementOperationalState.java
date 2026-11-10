package com.hope.trading.market_data.model;

public enum HistoricalEvidenceRetirementOperationalState {
    NOT_STARTED,
    PREFLIGHT_PASSED,
    BACKUP_VALIDATED,
    QUIESCENCE_CONFIRMED,
    PHYSICAL_IN_PROGRESS,
    PHYSICAL_RETIRED,
    FAILED,
    AMBIGUOUS,
    RECONCILIATION_REQUIRED
}
