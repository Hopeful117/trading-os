package com.hope.trading.market_intelligence.domain.opportunity;

import java.util.Objects;
import java.util.UUID;

/** Explicit reference to optional AI knowledge; it never replaces Observations. */
public record AiAnalysisReference(UUID analysisId) {
    public AiAnalysisReference { Objects.requireNonNull(analysisId, "analysisId"); }
}
