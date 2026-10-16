package com.hope.trading.market_intelligence.application.capability;

import com.hope.trading.market_intelligence.domain.*;
import com.hope.trading.market_intelligence.domain.artifact.*;
import com.hope.trading.market_intelligence.domain.capability.*;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class SpreadAnalysisCapability implements DeterministicAnalysisCapability, Capability {
    public static final String CAPABILITY_ID = "spread-analysis";
    public static final String CAPABILITY_VERSION = "1.1.0";
    private static final String SPREAD = "spread";
    private static final String SPREAD_PERCENTAGE = "spreadPercentage";
    private static final String MIDPOINT = "midpoint";
    private static final String RELATIVE_SPREAD = "relativeSpread";
    private static final String SPREAD_BPS = "spreadBps";
    private final QuoteQualityCalculator calculator;

    @Autowired
    public SpreadAnalysisCapability() {
        this(new QuoteQualityCalculator());
    }

    SpreadAnalysisCapability(QuoteQualityCalculator calculator) {
        this.calculator = calculator;
    }

    @Override
    public CapabilityMetadata metadata() {
        return new CapabilityMetadata(
                new CapabilityId(CAPABILITY_ID), new CapabilityVersion(CAPABILITY_VERSION),
                CapabilityCategory.DETERMINISTIC, ExecutionPolicy.ON_DEMAND,
                RetryPolicy.disabled(), List.of(new com.hope.trading.market_intelligence.domain.capability.ArtifactRequirement(
                ProductionArtifactTypes.MARKET_SNAPSHOT, ProductionArtifactTypes.V1,
                VersionCompatibilityMode.EXACT, true, ArtifactCardinality.ONE, false)),
                List.of(
                        new ProducedContribution.ArtifactContribution(
                                ProductionArtifactTypes.SPREAD_ANALYSIS,
                                ProductionArtifactTypes.V1, Set.of()),
                        new ProducedContribution.MetricContribution(MIDPOINT),
                        new ProducedContribution.MetricContribution(SPREAD),
                        new ProducedContribution.MetricContribution(SPREAD_PERCENTAGE),
                        new ProducedContribution.MetricContribution(RELATIVE_SPREAD),
                        new ProducedContribution.MetricContribution(SPREAD_BPS)),
                java.time.Duration.ofSeconds(1), null);
    }

    @Override
    public CapabilityResult execute(CapabilityContext context) {
        StoredArtifact input = context.resolvedArtifacts().values().stream()
                .flatMap(List::stream).findFirst().orElseThrow();
        MarketSnapshotContext snapshot = (MarketSnapshotContext) input.content();
        QuoteQualityAnalysis analysis = calculator.calculate(
                snapshot.marketId(), snapshot.bid(), snapshot.ask(), snapshot.occurredAt());
        if (analysis.status() != DeterministicAnalysisStatus.AVAILABLE) {
            return CapabilityResult.noOpportunity(List.of(analysis.status().name()),
                    analysis.status() == DeterministicAnalysisStatus.INSUFFICIENT_DATA
                            ? CapabilityCompleteness.PARTIAL : CapabilityCompleteness.DEGRADED);
        }
        Map<String, BigDecimal> measurements = measurements(analysis);
        Instant observedAt = snapshot.occurredAt();
        StoredArtifact output = outputArtifact(
                context, input, ProductionArtifactTypes.SPREAD_ANALYSIS,
                new DeterministicMeasurements(
                        "Market spread",
                        "Objective bid/ask spread calculated from the normalized market snapshot.",
                        measurements, observedAt), observedAt);
        return new CapabilityResult(
                metadata().producedContributions(),
                List.of(new ProducedArtifact(
                        ProductionArtifactTypes.SPREAD_ANALYSIS,
                        ProductionArtifactTypes.V1, output)),
                measurements, List.of(), CapabilityCompleteness.COMPLETE);
    }

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
        ContextSection section = context.section(ContextSectionType.MARKET_SNAPSHOT).orElseThrow();
        if (section.status() != ContextSectionStatus.AVAILABLE) {
            return CapabilityAnalysisResult.empty("Quote input is " + section.status());
        }
        MarketSnapshotContext snapshot = (MarketSnapshotContext) section.payload();
        QuoteQualityAnalysis analysis = calculator.calculate(
                snapshot.marketId(), snapshot.bid(), snapshot.ask(), snapshot.occurredAt());
        if (analysis.status() != DeterministicAnalysisStatus.AVAILABLE) {
            return CapabilityAnalysisResult.empty("Quote input is " + analysis.status());
        }
        Map<String, BigDecimal> measurements = measurements(analysis);
        Instant generatedAt = Instant.now();

        IntelligenceFinding finding = new IntelligenceFinding(
                CAPABILITY_ID + ":" + request.marketId() + ":" + snapshot.occurredAt(),
                CAPABILITY_ID,
                AnalysisOrigin.DETERMINISTIC,
                FindingType.DETERMINISTIC_FINDING,
                "Market spread",
                "Objective bid/ask spread calculated from the normalized market snapshot.",
                Map.of(
                         MIDPOINT, measurements.get(MIDPOINT),
                         SPREAD, measurements.get(SPREAD),
                         SPREAD_PERCENTAGE, measurements.get(SPREAD_PERCENTAGE),
                         RELATIVE_SPREAD, measurements.get(RELATIVE_SPREAD),
                         SPREAD_BPS, measurements.get(SPREAD_BPS)
                ),
                BigDecimal.ONE,
                Set.of(ContextSectionType.MARKET_SNAPSHOT),
                generatedAt
        );
        return new CapabilityAnalysisResult(List.of(finding), List.of());
    }

    private Map<String, BigDecimal> measurements(QuoteQualityAnalysis analysis) {
        return Map.of(
                MIDPOINT, analysis.midpoint(),
                SPREAD, analysis.absoluteSpread(),
                RELATIVE_SPREAD, analysis.relativeSpread(),
                SPREAD_BPS, analysis.spreadBps(),
                SPREAD_PERCENTAGE, analysis.relativeSpread().multiply(BigDecimal.valueOf(100))
                        .setScale(8, RoundingMode.HALF_UP));
    }

    private StoredArtifact outputArtifact(
            CapabilityContext context, StoredArtifact input, ArtifactType type,
            ArtifactContent content, Instant observedAt) {
        ArtifactIdentity identity = new ArtifactIdentity(
                type.value(), CAPABILITY_ID, CAPABILITY_VERSION);
        ArtifactCacheKey key = new ArtifactCacheKey(
                identity, input.key().scope(), ArtifactFingerprint.empty(),
                ArtifactFingerprint.ofInputs(List.of(input.key().inputFingerprint().value())));
        return new StoredArtifact(
                key, content,
                ArtifactFreshness.validUntil(observedAt, observedAt.plusSeconds(30),
                        input.freshness().sourceVersion()),
                new ArtifactProvenance(
                        CAPABILITY_ID, CAPABILITY_VERSION, context.capabilityExecutionId(),
                        observedAt, Set.of(input.key().identity()), Set.of()),
                 com.hope.trading.market_intelligence.domain.execution.AnalysisResultQuality.COMPLETE,
                 ProductionArtifactTypes.V1);
    }
}
