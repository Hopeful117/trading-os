package com.hope.trading.market_intelligence.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record QuoteQualityAnalysis(
        UUID marketId,
        Instant evidenceAt,
        BigDecimal midpoint,
        BigDecimal absoluteSpread,
        BigDecimal relativeSpread,
        BigDecimal spreadBps,
        DeterministicAnalysisStatus status,
        String algorithmVersion
) {
    public static final String ALGORITHM_VERSION = "QUOTE_QUALITY_V1";

    public QuoteQualityAnalysis {
        if (status == null) throw new IllegalArgumentException("status is required");
        if (algorithmVersion == null || algorithmVersion.isBlank()) {
            throw new IllegalArgumentException("algorithmVersion is required");
        }
    }
}
