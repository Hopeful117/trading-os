package com.hope.trading.market_intelligence.application.capability;

import com.hope.trading.market_intelligence.domain.*;
import com.hope.trading.market_intelligence.domain.artifact.*;
import com.hope.trading.market_intelligence.domain.capability.*;
import com.hope.trading.market_intelligence.domain.execution.AnalysisResultQuality;
import com.hope.trading.market_intelligence.domain.momentum.MomentumAssessment;
import com.hope.trading.market_intelligence.domain.momentum.MomentumCalculator;
import com.hope.trading.market_intelligence.domain.momentum.MomentumCapabilityContent;
import com.hope.trading.market_intelligence.domain.momentum.MomentumPolicy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Component
public class OhlcMomentumAnalysisCapability implements DeterministicAnalysisCapability, Capability {
    public static final String CAPABILITY_ID = "ohlc-momentum-analysis";
    public static final String CAPABILITY_VERSION = "1.0.0";

    private final MomentumCalculator calculator;
    private final MomentumPolicy policy;

    public OhlcMomentumAnalysisCapability() {
        this(new MomentumCalculator(), MomentumPolicy.V1);
    }

    OhlcMomentumAnalysisCapability(MomentumCalculator calculator, MomentumPolicy policy) {
        this.calculator = calculator;
        this.policy = policy;
    }

    @Override
    public CapabilityMetadata metadata() {
        return new CapabilityMetadata(
                new CapabilityId(CAPABILITY_ID), new CapabilityVersion(CAPABILITY_VERSION),
                CapabilityCategory.DETERMINISTIC, ExecutionPolicy.ON_DEMAND,
                RetryPolicy.disabled(), List.of(new com.hope.trading.market_intelligence.domain.capability.ArtifactRequirement(
                ProductionArtifactTypes.OHLC_HISTORY, ProductionArtifactTypes.V1,
                VersionCompatibilityMode.EXACT, true, ArtifactCardinality.ONE, false)),
                List.of(new ProducedContribution.ArtifactContribution(
                        ProductionArtifactTypes.OHLC_MOMENTUM_ANALYSIS,
                        ProductionArtifactTypes.V1, Set.of()),
                        new ProducedContribution.MetricContribution("normalizedSlope"),
                        new ProducedContribution.MetricContribution("directionalPersistence"),
                        new ProducedContribution.MetricContribution("precedingMovement"),
                        new ProducedContribution.MetricContribution("recentMovement")),
                Duration.ofSeconds(1), null);
    }

    @Override
    public CapabilityResult execute(CapabilityContext context) {
        StoredArtifact input = context.resolvedArtifacts().values().stream()
                .flatMap(List::stream).findFirst().orElseThrow();
        HistoricalOhlcContext history = (HistoricalOhlcContext) input.content();
        MomentumAssessment assessment = calculator.calculate(
                history, context.evaluationCutoff(), input.key().inputFingerprint().value(), policy);
        Instant producedAt = assessment.cutoff() == null ? Instant.now() : assessment.cutoff();
        ArtifactIdentity identity = new ArtifactIdentity(
                ProductionArtifactTypes.OHLC_MOMENTUM_ANALYSIS.value(), CAPABILITY_ID, CAPABILITY_VERSION);
        StoredArtifact output = new StoredArtifact(
                new ArtifactCacheKey(identity, input.key().scope(),
                        ArtifactFingerprint.ofParameters(Map.of("policyVersion", policy.version())),
                        ArtifactFingerprint.ofInputs(List.of(input.key().inputFingerprint().value()))),
                new MomentumCapabilityContent(assessment, assessment.status().name(),
                        List.of(assessment.reason()), producedAt.plusSeconds(900)),
                ArtifactFreshness.validUntil(producedAt, producedAt.plusSeconds(900), input.freshness().sourceVersion()),
                new ArtifactProvenance(CAPABILITY_ID, CAPABILITY_VERSION, context.capabilityExecutionId(),
                        producedAt, Set.of(input.key().identity()), Set.of())
                        .withEvidenceFingerprint(assessment.resultFingerprint()),
                assessment.status() == MomentumAssessment.Status.AVAILABLE
                        ? AnalysisResultQuality.COMPLETE : AnalysisResultQuality.PARTIAL,
                ProductionArtifactTypes.V1);
        Map<String, BigDecimal> metrics = metrics(assessment);
        CapabilityCompleteness completeness = assessment.status() == MomentumAssessment.Status.AVAILABLE
                ? CapabilityCompleteness.COMPLETE : CapabilityCompleteness.PARTIAL;
        return new CapabilityResult(metadata().producedContributions(),
                List.of(new ProducedArtifact(ProductionArtifactTypes.OHLC_MOMENTUM_ANALYSIS,
                        ProductionArtifactTypes.V1, output)), metrics,
                assessment.status() == MomentumAssessment.Status.AVAILABLE
                        ? List.of() : List.of(assessment.status().name()), completeness);
    }

    @Override
    public String id() { return CAPABILITY_ID; }

    @Override
    public Set<AnalysisExecutionMode> supportedModes() {
        return Set.of(AnalysisExecutionMode.PASSIVE, AnalysisExecutionMode.ACTIVE);
    }

    @Override
    public List<ContextRequirement> requirements(AnalysisExecutionMode mode) {
        return List.of(ContextRequirement.requiredPublic(ContextSectionType.HISTORICAL_OHLC));
    }

    @Override
    public CapabilityAvailability availability() { return CapabilityAvailability.AVAILABLE; }

    @Override
    public CapabilityAnalysisResult analyze(IntelligenceAnalysisRequest request, IntelligenceContext context) {
        ContextSection section = context.section(ContextSectionType.HISTORICAL_OHLC).orElseThrow();
        if (section.status() != ContextSectionStatus.AVAILABLE) {
            return CapabilityAnalysisResult.empty("OHLC input is " + section.status());
        }
        HistoricalOhlcContext history = (HistoricalOhlcContext) section.payload();
        MomentumAssessment assessment = calculator.calculate(history,
                context.evaluationCutoff().orElse(null), ArtifactFingerprint.empty().value(), policy);
        if (assessment.status() != MomentumAssessment.Status.AVAILABLE) {
            return CapabilityAnalysisResult.empty("OHLC input is " + assessment.status());
        }
        return new CapabilityAnalysisResult(List.of(new IntelligenceFinding(
                CAPABILITY_ID + ":" + request.marketId() + ":" + history.interval(), CAPABILITY_ID,
                AnalysisOrigin.DETERMINISTIC, FindingType.DETERMINISTIC_FINDING,
                "OHLC Momentum", assessment.reason(), metrics(assessment), BigDecimal.ONE,
                Set.of(ContextSectionType.HISTORICAL_OHLC), assessment.cutoff())), List.of());
    }

    private Map<String, BigDecimal> metrics(MomentumAssessment assessment) {
        if (assessment.status() != MomentumAssessment.Status.AVAILABLE) return Map.of();
        return Map.of("normalizedSlope", assessment.normalizedSlope(),
                "directionalPersistence", assessment.directionalPersistence(),
                "precedingMovement", assessment.precedingMovement(),
                "recentMovement", assessment.recentMovement());
    }
}
