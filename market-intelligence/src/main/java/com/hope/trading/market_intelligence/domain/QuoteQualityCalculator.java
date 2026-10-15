package com.hope.trading.market_intelligence.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

public final class QuoteQualityCalculator {
    private static final int RELATIVE_SCALE = 16;
    private static final int OUTPUT_SCALE = 8;

    public QuoteQualityAnalysis calculate(
            UUID marketId,
            BigDecimal bid,
            BigDecimal ask,
            Instant evidenceAt
    ) {
        if (bid == null || ask == null) {
            return unavailable(marketId, evidenceAt, DeterministicAnalysisStatus.INSUFFICIENT_DATA);
        }
        if (bid.signum() <= 0 || ask.signum() <= 0 || bid.compareTo(ask) > 0) {
            return unavailable(marketId, evidenceAt, DeterministicAnalysisStatus.INVALID_INPUT);
        }

        BigDecimal midpoint = bid.add(ask).divide(BigDecimal.valueOf(2));
        if (midpoint.signum() == 0) {
            return unavailable(marketId, evidenceAt, DeterministicAnalysisStatus.INVALID_INPUT);
        }
        BigDecimal absoluteSpread = ask.subtract(bid);
        BigDecimal relativeSpread = absoluteSpread.divide(midpoint, RELATIVE_SCALE, RoundingMode.HALF_UP);
        BigDecimal spreadBps = relativeSpread.multiply(BigDecimal.valueOf(10_000))
                .setScale(OUTPUT_SCALE, RoundingMode.HALF_UP);
        return new QuoteQualityAnalysis(
                marketId, evidenceAt, midpoint, absoluteSpread, relativeSpread, spreadBps,
                DeterministicAnalysisStatus.AVAILABLE, QuoteQualityAnalysis.ALGORITHM_VERSION);
    }

    private QuoteQualityAnalysis unavailable(
            UUID marketId, Instant evidenceAt, DeterministicAnalysisStatus status) {
        return new QuoteQualityAnalysis(
                marketId, evidenceAt, null, null, null, null, status,
                QuoteQualityAnalysis.ALGORITHM_VERSION);
    }
}
