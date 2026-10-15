package com.hope.trading.market_intelligence.domain;

/** Selects the temporal source policy used while assembling analysis context. */
public enum EvidenceAcquisitionPolicy {
    LIVE_CURRENT,
    STRICT_OBSERVED
}
