package com.hope.trading.market_intelligence.application.capability;

import com.hope.trading.market_intelligence.domain.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class OhlcRangeAnalysisCapability implements DeterministicAnalysisCapability {
    public static final String CAPABILITY_ID = "ohlc-range-analysis";

    @Override
    public String id() {
        return CAPABILITY_ID;
    }

    @Override
    public Set<AnalysisExecutionMode> supportedModes() {
        return Set.of(AnalysisExecutionMode.ACTIVE);
    }

    @Override
    public List<ContextRequirement> requirements(AnalysisExecutionMode mode) {
        return List.of(
                ContextRequirement.requiredPublic(ContextSectionType.HISTORICAL_OHLC)
        );
    }

    @Override
    public CapabilityAvailability availability() {
        return CapabilityAvailability.AVAILABLE;
    }

    @Override
    public CapabilityAnalysisResult analyze(
            IntelligenceAnalysisRequest request,
            IntelligenceContext context
    ) {
        HistoricalOhlcContext history = (HistoricalOhlcContext) context
                .section(ContextSectionType.HISTORICAL_OHLC)
                .orElseThrow()
                .payload();
        if (history.candles().isEmpty()) {
            return CapabilityAnalysisResult.empty("OHLC history is empty");
        }

        BigDecimal highest = history.candles().stream()
                .map(OhlcPoint::high)
                .max(BigDecimal::compareTo)
                .orElseThrow();
        BigDecimal lowest = history.candles().stream()
                .map(OhlcPoint::low)
                .min(BigDecimal::compareTo)
                .orElseThrow();
        BigDecimal range = highest.subtract(lowest);
        BigDecimal rangePercentage = lowest.signum() == 0
                ? BigDecimal.ZERO
                : range.multiply(BigDecimal.valueOf(100))
                    .divide(lowest.abs(), 8, RoundingMode.HALF_UP);
        Instant generatedAt = Instant.now();

        return new CapabilityAnalysisResult(
                List.of(new IntelligenceFinding(
                        CAPABILITY_ID + ":" + request.marketId() + ":" + history.interval(),
                        CAPABILITY_ID,
                        AnalysisOrigin.DETERMINISTIC,
                        FindingType.DETERMINISTIC_FINDING,
                        "Historical price range",
                        "Objective high-to-low range over the loaded OHLC context.",
                        Map.of(
                                "highestPrice", highest,
                                "lowestPrice", lowest,
                                "range", range,
                                "rangePercentage", rangePercentage
                        ),
                        BigDecimal.ONE,
                        Set.of(ContextSectionType.HISTORICAL_OHLC),
                        generatedAt
                )),
                List.of()
        );
    }
}
