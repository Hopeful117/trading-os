package com.hope.trading.market_intelligence.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OhlcTemporalAnalysis(
        UUID marketId,
        String interval,
        Instant evidenceStart,
        Instant evidenceEnd,
        int candleCount,
        BigDecimal highestPrice,
        BigDecimal lowestPrice,
        BigDecimal range,
        BigDecimal closePrice,
        BigDecimal priceChange,
        BigDecimal simpleReturn,
        DeterministicAnalysisStatus simpleReturnStatus,
        DeterministicAnalysisStatus status,
        String algorithmVersion
) {
    public static final String ALGORITHM_VERSION = "OHLC_TEMPORAL_V1";

    public OhlcTemporalAnalysis {
        if (interval == null || interval.isBlank()) {
            throw new IllegalArgumentException("interval is required");
        }
        if (candleCount < 0) throw new IllegalArgumentException("candleCount must not be negative");
        if (status == null) throw new IllegalArgumentException("status is required");
        if (simpleReturnStatus == null) throw new IllegalArgumentException("simpleReturnStatus is required");
        if (algorithmVersion == null || algorithmVersion.isBlank()) {
            throw new IllegalArgumentException("algorithmVersion is required");
        }
    }
}
