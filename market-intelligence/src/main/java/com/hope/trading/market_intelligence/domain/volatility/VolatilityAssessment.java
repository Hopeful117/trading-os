package com.hope.trading.market_intelligence.domain.volatility;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record VolatilityAssessment(
        UUID marketId,
        String interval,
        Instant cutoff,
        Status status,
        int atrPeriod,
        int baselineLength,
        BigDecimal atr,
        BigDecimal normalizedAtr,
        BigDecimal realizedVolatility,
        BigDecimal compressionRatio,
        BigDecimal directionalEfficiency,
        State state,
        String algorithmVersion,
        String inputFingerprint,
        String resultFingerprint,
        String reason
) {
    public static final String ALGORITHM_VERSION = "OHLC_VOLATILITY_V1";

    public enum Status { AVAILABLE, INSUFFICIENT_DATA, INVALID_INPUT }
    public enum State { COMPRESSION, NORMAL, EXPANSION, INSUFFICIENT_EVIDENCE }
}
