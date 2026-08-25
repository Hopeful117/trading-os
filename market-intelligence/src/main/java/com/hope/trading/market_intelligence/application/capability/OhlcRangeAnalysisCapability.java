package com.hope.trading.market_intelligence.application.capability;

import com.hope.trading.market_intelligence.domain.*;
import com.hope.trading.market_intelligence.domain.artifact.*;
import com.hope.trading.market_intelligence.domain.capability.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class OhlcRangeAnalysisCapability implements DeterministicAnalysisCapability, Capability {
    public static final String CAPABILITY_ID = "ohlc-range-analysis";
    public static final String CAPABILITY_VERSION = "1.0.0";

    @Override
    public CapabilityMetadata metadata() {
        return new CapabilityMetadata(
                new CapabilityId(CAPABILITY_ID), new CapabilityVersion(CAPABILITY_VERSION),
                CapabilityCategory.DETERMINISTIC, ExecutionPolicy.ON_DEMAND,
                RetryPolicy.disabled(), List.of(new com.hope.trading.market_intelligence.domain.capability.ArtifactRequirement(
                ProductionArtifactTypes.OHLC_HISTORY, ProductionArtifactTypes.V1,
                VersionCompatibilityMode.EXACT, true, ArtifactCardinality.ONE, false)),
                List.of(
                        new ProducedContribution.ArtifactContribution(
                                ProductionArtifactTypes.OHLC_RANGE_ANALYSIS,
                                ProductionArtifactTypes.V1, Set.of()),
                        new ProducedContribution.MetricContribution("highestPrice"),
                        new ProducedContribution.MetricContribution("lowestPrice"),
                        new ProducedContribution.MetricContribution("closePrice"),
                        new ProducedContribution.MetricContribution("range"),
                        new ProducedContribution.MetricContribution("rangePercentage"),
                        new ProducedContribution.MetricContribution("priceChange")),
                java.time.Duration.ofSeconds(1), null);
    }

    @Override
    public CapabilityResult execute(CapabilityContext context) {
        StoredArtifact input = context.resolvedArtifacts().values().stream()
                .flatMap(List::stream).findFirst().orElseThrow();
        HistoricalOhlcContext history = (HistoricalOhlcContext) input.content();
        if (history.candles().isEmpty()) {
            return CapabilityResult.noOpportunity(List.of("OHLC history is empty"));
        }
        Map<String, BigDecimal> measurements = calculate(history);
        Instant observedAt = history.candles().getLast().closeTime();
        ArtifactIdentity identity = new ArtifactIdentity(
                ProductionArtifactTypes.OHLC_RANGE_ANALYSIS.value(),
                CAPABILITY_ID, CAPABILITY_VERSION);
        StoredArtifact output = new StoredArtifact(
                new ArtifactCacheKey(
                        identity, input.key().scope(), ArtifactFingerprint.empty(),
                        ArtifactFingerprint.ofInputs(List.of(input.key().inputFingerprint().value()))),
                new DeterministicMeasurements(
                        "Historical price range",
                        "Objective high-to-low range over the loaded OHLC context.",
                        measurements, observedAt),
                ArtifactFreshness.validUntil(
                        observedAt, observedAt.plusSeconds(900), input.freshness().sourceVersion()),
                new ArtifactProvenance(
                        CAPABILITY_ID, CAPABILITY_VERSION, context.capabilityExecutionId(),
                        observedAt, Set.of(input.key().identity()), Set.of()),
                com.hope.trading.market_intelligence.domain.execution.AnalysisResultQuality.COMPLETE);
        return new CapabilityResult(
                metadata().producedContributions(),
                List.of(new ProducedArtifact(
                        ProductionArtifactTypes.OHLC_RANGE_ANALYSIS,
                        ProductionArtifactTypes.V1, output)),
                measurements, List.of(), CapabilityCompleteness.COMPLETE);
    }

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

        Map<String, BigDecimal> measurements = calculate(history);
        Instant generatedAt = Instant.now();

        return new CapabilityAnalysisResult(
                List.of(new IntelligenceFinding(
                        CAPABILITY_ID + ":" + request.marketId() + ":" + history.interval(),
                        CAPABILITY_ID,
                        AnalysisOrigin.DETERMINISTIC,
                        FindingType.DETERMINISTIC_FINDING,
                        "Historical price range",
                        "Objective high-to-low range over the loaded OHLC context.",
                        measurements,
                        BigDecimal.ONE,
                        Set.of(ContextSectionType.HISTORICAL_OHLC),
                        generatedAt
                )),
                List.of()
        );
    }

    private Map<String, BigDecimal> calculate(HistoricalOhlcContext history) {
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
        BigDecimal priceChange = history.candles().getLast().close()
                .subtract(history.candles().getFirst().open());
        return Map.of(
                "highestPrice", highest, "lowestPrice", lowest,
                "closePrice", history.candles().getLast().close(),
                "range", range, "rangePercentage", rangePercentage,
                "priceChange", priceChange);
    }
}
