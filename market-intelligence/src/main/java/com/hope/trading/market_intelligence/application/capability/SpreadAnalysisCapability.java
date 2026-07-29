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
public class SpreadAnalysisCapability implements DeterministicAnalysisCapability {
    public static final String CAPABILITY_ID = "spread-analysis";

    @Override
    public String id() {
        return CAPABILITY_ID;
    }

    @Override
    public Set<AnalysisExecutionMode> supportedModes() {
        return Set.of(AnalysisExecutionMode.PASSIVE, AnalysisExecutionMode.ACTIVE);
    }

    @Override
    public List<ContextRequirement> requirements(AnalysisExecutionMode mode) {
        return List.of(
                ContextRequirement.requiredPublic(ContextSectionType.MARKET_SNAPSHOT)
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
        ContextSection section = context.section(ContextSectionType.MARKET_SNAPSHOT)
                .orElseThrow();
        MarketSnapshotContext snapshot = (MarketSnapshotContext) section.payload();
        if (snapshot.bid() == null || snapshot.ask() == null) {
            return CapabilityAnalysisResult.empty("Bid or ask unavailable");
        }

        BigDecimal spread = snapshot.ask().subtract(snapshot.bid());
        BigDecimal midpoint = snapshot.ask().add(snapshot.bid())
                .divide(BigDecimal.valueOf(2), 12, RoundingMode.HALF_UP);
        BigDecimal spreadPercentage = midpoint.signum() == 0
                ? BigDecimal.ZERO
                : spread.multiply(BigDecimal.valueOf(100))
                    .divide(midpoint, 8, RoundingMode.HALF_UP);
        Instant generatedAt = Instant.now();

        IntelligenceFinding finding = new IntelligenceFinding(
                CAPABILITY_ID + ":" + request.marketId() + ":" + snapshot.occurredAt(),
                CAPABILITY_ID,
                AnalysisOrigin.DETERMINISTIC,
                FindingType.DETERMINISTIC_FINDING,
                "Market spread",
                "Objective bid/ask spread calculated from the normalized market snapshot.",
                Map.of(
                        "spread", spread,
                        "spreadPercentage", spreadPercentage
                ),
                BigDecimal.ONE,
                Set.of(ContextSectionType.MARKET_SNAPSHOT),
                generatedAt
        );
        return new CapabilityAnalysisResult(List.of(finding), List.of());
    }
}
