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
public class SpreadAnalysisCapability implements DeterministicAnalysisCapability, Capability {
    public static final String CAPABILITY_ID = "spread-analysis";
    public static final String CAPABILITY_VERSION = "1.0.0";

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
                        new ProducedContribution.MetricContribution("spread"),
                        new ProducedContribution.MetricContribution("spreadPercentage")),
                java.time.Duration.ofSeconds(1), null);
    }

    @Override
    public CapabilityResult execute(CapabilityContext context) {
        StoredArtifact input = context.resolvedArtifacts().values().stream()
                .flatMap(List::stream).findFirst().orElseThrow();
        MarketSnapshotContext snapshot = (MarketSnapshotContext) input.content();
        if (snapshot.bid() == null || snapshot.ask() == null) {
            return CapabilityResult.noOpportunity(List.of("Bid or ask unavailable"));
        }
        Map<String, BigDecimal> measurements = calculate(snapshot.bid(), snapshot.ask());
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
        ContextSection section = context.section(ContextSectionType.MARKET_SNAPSHOT)
                .orElseThrow();
        MarketSnapshotContext snapshot = (MarketSnapshotContext) section.payload();
        if (snapshot.bid() == null || snapshot.ask() == null) {
            return CapabilityAnalysisResult.empty("Bid or ask unavailable");
        }

        Map<String, BigDecimal> measurements = calculate(snapshot.bid(), snapshot.ask());
        Instant generatedAt = Instant.now();

        IntelligenceFinding finding = new IntelligenceFinding(
                CAPABILITY_ID + ":" + request.marketId() + ":" + snapshot.occurredAt(),
                CAPABILITY_ID,
                AnalysisOrigin.DETERMINISTIC,
                FindingType.DETERMINISTIC_FINDING,
                "Market spread",
                "Objective bid/ask spread calculated from the normalized market snapshot.",
                Map.of(
                        "spread", measurements.get("spread"),
                        "spreadPercentage", measurements.get("spreadPercentage")
                ),
                BigDecimal.ONE,
                Set.of(ContextSectionType.MARKET_SNAPSHOT),
                generatedAt
        );
        return new CapabilityAnalysisResult(List.of(finding), List.of());
    }

    private Map<String, BigDecimal> calculate(BigDecimal bid, BigDecimal ask) {
        BigDecimal spread = ask.subtract(bid);
        BigDecimal midpoint = ask.add(bid)
                .divide(BigDecimal.valueOf(2), 12, RoundingMode.HALF_UP);
        BigDecimal percentage = midpoint.signum() == 0 ? BigDecimal.ZERO
                : spread.multiply(BigDecimal.valueOf(100))
                .divide(midpoint, 8, RoundingMode.HALF_UP);
        return Map.of("spread", spread, "spreadPercentage", percentage);
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
                com.hope.trading.market_intelligence.domain.execution.AnalysisResultQuality.COMPLETE);
    }
}
