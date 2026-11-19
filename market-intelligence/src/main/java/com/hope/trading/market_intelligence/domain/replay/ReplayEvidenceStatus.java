package com.hope.trading.market_intelligence.domain.replay;

public enum ReplayEvidenceStatus {
    COMPLETE,
    MISSING,
    EXCLUDED,
    STALE,
    CONTRADICTORY,
    GAPPED,
    INCOMPLETE
}
