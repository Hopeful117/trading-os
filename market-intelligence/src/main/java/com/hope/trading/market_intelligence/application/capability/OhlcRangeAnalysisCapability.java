package com.hope.trading.market_intelligence.application.capability;

import com.hope.trading.market_intelligence.domain.*;
import com.hope.trading.market_intelligence.domain.artifact.*;
import com.hope.trading.market_intelligence.domain.capability.*;
import com.hope.trading.market_intelligence.domain.execution.AnalysisResultQuality;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class OhlcRangeAnalysisCapability implements DeterministicAnalysisCapability, Capability {
    public static final String CAPABILITY_ID = "ohlc-range-analysis";
    public static final String CAPABILITY_VERSION = "1.1.0";
    private final OhlcTemporalCalculator calculator;

    public OhlcRangeAnalysisCapability() {
        this(new OhlcTemporalCalculator());
    }

    OhlcRangeAnalysisCapability(OhlcTemporalCalculator calculator) {
        this.calculator = calculator;
    }

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
                        new ProducedContribution.MetricContribution("priceChange"),
                        new ProducedContribution.MetricContribution("simpleReturn")),
                java.time.Duration.ofSeconds(1), null);
    }

    @Override
    public CapabilityResult execute(CapabilityContext context) {
        StoredArtifact input = context.resolvedArtifacts().values().stream()
                .flatMap(List::stream).findFirst().orElseThrow();
        HistoricalOhlcContext history = (HistoricalOhlcContext) input.content();
        OhlcTemporalAnalysis analysis = calculator.calculate(history, null);
        if (analysis.status() != DeterministicAnalysisStatus.AVAILABLE) {
            return CapabilityResult.noOpportunity(List.of(analysis.status().name()),
                    analysis.status() == DeterministicAnalysisStatus.INSUFFICIENT_DATA
                            ? CapabilityCompleteness.PARTIAL : CapabilityCompleteness.DEGRADED);
        }
        Map<String, BigDecimal> measurements = measurements(analysis);
        Instant observedAt = analysis.evidenceEnd();
        boolean partial = analysis.simpleReturnStatus() != DeterministicAnalysisStatus.AVAILABLE;
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
                partial ? AnalysisResultQuality.PARTIAL : AnalysisResultQuality.COMPLETE);
        return new CapabilityResult(
                metadata().producedContributions(),
                List.of(new ProducedArtifact(
                        ProductionArtifactTypes.OHLC_RANGE_ANALYSIS,
                        ProductionArtifactTypes.V1, output)),
                measurements, partial ? List.of("SIMPLE_RETURN_" + analysis.simpleReturnStatus().name()) : List.of(),
                partial ? CapabilityCompleteness.PARTIAL : CapabilityCompleteness.COMPLETE);
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
        ContextSection section = context.section(ContextSectionType.HISTORICAL_OHLC).orElseThrow();
        if (section.status() != ContextSectionStatus.AVAILABLE) {
            return CapabilityAnalysisResult.empty("OHLC input is " + section.status());
        }
        HistoricalOhlcContext history = (HistoricalOhlcContext) section.payload();
        OhlcTemporalAnalysis analysis = calculator.calculate(history, context.evaluationCutoff().orElse(null));
        if (analysis.status() != DeterministicAnalysisStatus.AVAILABLE) {
            return CapabilityAnalysisResult.empty("OHLC input is " + analysis.status());
        }
        Map<String, BigDecimal> measurements = measurements(analysis);
        Instant generatedAt = analysis.evidenceEnd();

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

    private Map<String, BigDecimal> measurements(OhlcTemporalAnalysis analysis) {
        BigDecimal rangePercentage = analysis.lowestPrice().signum() == 0
                ? BigDecimal.ZERO
                : analysis.range().multiply(BigDecimal.valueOf(100))
                    .divide(analysis.lowestPrice().abs(), 8, RoundingMode.HALF_UP);
        Map<String, BigDecimal> measurements = new HashMap<>();
        measurements.put("highestPrice", analysis.highestPrice());
        measurements.put("lowestPrice", analysis.lowestPrice());
        measurements.put("closePrice", analysis.closePrice());
        measurements.put("range", analysis.range());
        measurements.put("rangePercentage", rangePercentage);
        measurements.put("priceChange", analysis.priceChange());
        if (analysis.simpleReturn() != null) {
            measurements.put("simpleReturn", analysis.simpleReturn());
        }
        return Map.copyOf(measurements);
    }
}
