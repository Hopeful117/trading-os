package com.hope.trading.market_intelligence.application.capability;

import com.hope.trading.market_intelligence.domain.*;
import com.hope.trading.market_intelligence.domain.artifact.*;
import com.hope.trading.market_intelligence.domain.capability.*;
import com.hope.trading.market_intelligence.domain.trendcontext.*;
import com.hope.trading.market_intelligence.domain.marketstructure.MarketStructureArtifactContent;
import com.hope.trading.market_intelligence.domain.marketstructure.MarketStructureAvailability;
import com.hope.trading.market_intelligence.domain.marketstructure.MarketStructureResult;
import com.hope.trading.market_intelligence.adapter.marketdata.TrendContextRoleHistory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Component
public class TrendContextAnalysisCapability implements DeterministicAnalysisCapability, Capability {
    public static final String CAPABILITY_ID = "trend-context-analysis";
    public static final String CAPABILITY_VERSION = "1.0.0";

    private final com.hope.trading.market_intelligence.adapter.marketdata.TrendContextInputMapper mapper;
    private final TrendContextEngine engine;
    private final TrendContextProfile profile;

    public TrendContextAnalysisCapability(
            com.hope.trading.market_intelligence.adapter.marketdata.TrendContextInputMapper inputMapper,
            TrendContextEngine engine,
            TrendContextProfile profile) {
        this.mapper = inputMapper;
        this.engine = engine;
        this.profile = profile;
    }

    @Override
    public CapabilityMetadata metadata() {
        return new CapabilityMetadata(
                new CapabilityId(CAPABILITY_ID), new CapabilityVersion(CAPABILITY_VERSION),
                CapabilityCategory.DETERMINISTIC, ExecutionPolicy.ON_DEMAND,
                RetryPolicy.disabled(),
                List.of(new com.hope.trading.market_intelligence.domain.capability.ArtifactRequirement(
                        ProductionArtifactTypes.TREND_CONTEXT_HISTORY,
                        ProductionArtifactTypes.V1, VersionCompatibilityMode.EXACT,
                        true, ArtifactCardinality.ONE, true),
                        new com.hope.trading.market_intelligence.domain.capability.ArtifactRequirement(
                                ProductionArtifactTypes.MARKET_STRUCTURE,
                                ProductionArtifactTypes.V1, VersionCompatibilityMode.EXACT,
                                false, ArtifactCardinality.ZERO_OR_MORE, true)),
                List.of(new ProducedContribution.ArtifactContribution(
                        ProductionArtifactTypes.TREND_CONTEXT_ASSESSMENT,
                        ProductionArtifactTypes.V1, Set.of())),
                Duration.ofSeconds(5), null);
    }

    @Override
    public CapabilityResult execute(CapabilityContext context) {
        Optional<StoredArtifact> input = context.resolvedArtifacts().values().stream()
                .flatMap(Collection::stream)
                .filter(value -> value.content() instanceof TrendContextRoleHistory).findFirst();
        if (input.isEmpty() || !(input.get().content() instanceof TrendContextRoleHistory history)) {
            return new CapabilityResult(List.of(), List.of(), Map.of(),
                    List.of("Trend Context history is unavailable"), CapabilityCompleteness.DEGRADED);
        }

        TrendContextAssessmentInput assessmentInput;
        try {
            assessmentInput = mapper.map(history.responsesByRole(), profile,
                    history.assessmentAt(), history.cutOffAt(), history.ruleVersion());
        } catch (RuntimeException exception) {
            return new CapabilityResult(List.of(), List.of(), Map.of(),
                    List.of("Trend Context input rejected: " + safeMessage(exception)),
                    CapabilityCompleteness.DEGRADED);
        }

        EnumMap<TrendContextRole, MarketStructureResult> structures = new EnumMap<>(TrendContextRole.class);
        List<MarketStructureResult> structuralResults = context.resolvedArtifacts().values().stream()
                .flatMap(Collection::stream)
                .map(StoredArtifact::content)
                .filter(MarketStructureArtifactContent.class::isInstance)
                .map(MarketStructureArtifactContent.class::cast)
                .map(MarketStructureArtifactContent::result).toList();
        for (TrendContextRole role : TrendContextRole.values()) {
            TrendContextRoleSeries series = assessmentInput.roleSeries().get(role);
            if (series == null) continue;
            Optional<MarketStructureResult> structuralResult = structuralResults.stream()
                    .filter(value -> value.interval().equals(series.interval())).findFirst();
            if (structuralResult.isEmpty()) {
                if (!profile.roles().get(role).required()) continue;
                return new CapabilityResult(List.of(), List.of(), Map.of(),
                        List.of("Market Structure artifact is unavailable for " + series.interval()),
                        CapabilityCompleteness.DEGRADED);
            }
            if (structuralResult.get().availability() != MarketStructureAvailability.AVAILABLE) {
                if (!profile.roles().get(role).required()) continue;
                return new CapabilityResult(List.of(), List.of(), Map.of(),
                        List.of("Market Structure evidence is "
                                + structuralResult.get().availability().name().toLowerCase()
                                + " for " + series.interval()), CapabilityCompleteness.DEGRADED);
            }
            structures.put(role, structuralResult.get());
        }
        TrendContextAssessment assessment = engine.assess(assessmentInput, structures);
        TrendContextCapabilityContent content = content(assessmentInput, assessment);
        Instant observedAt = assessment.assessmentAt();
        StoredArtifact output = new StoredArtifact(
                new ArtifactCacheKey(
                        new ArtifactIdentity(ProductionArtifactTypes.TREND_CONTEXT_ASSESSMENT.value(),
                                CAPABILITY_ID, CAPABILITY_VERSION),
                        ArtifactScope.publicMarket(assessment.marketId(), "TREND_CONTEXT", AnalysisExecutionMode.ACTIVE),
                        ArtifactFingerprint.empty(),
                        new ArtifactFingerprint(assessment.inputFingerprint())),
                content,
                ArtifactFreshness.validUntil(observedAt,
                        observedAt.plus(profile.roles().get(TrendContextRole.SETUP).intervalDuration()
                                .multipliedBy(profile.freshnessMultiplier())),
                        assessment.inputFingerprint()),
                new ArtifactProvenance(CAPABILITY_ID, CAPABILITY_VERSION,
                        context.capabilityExecutionId(), observedAt,
                        Set.of(input.get().key().identity()), Set.of()),
                com.hope.trading.market_intelligence.domain.execution.AnalysisResultQuality.COMPLETE);
        return new CapabilityResult(
                metadata().producedContributions(),
                List.of(new ProducedArtifact(
                        ProductionArtifactTypes.TREND_CONTEXT_ASSESSMENT,
                        ProductionArtifactTypes.V1, output)),
                Map.of("assessment", BigDecimal.ONE),
                assessment.findings().stream().map(value -> value.code()).toList(),
                CapabilityCompleteness.COMPLETE);
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
        return List.of(ContextRequirement.optionalPublic(ContextSectionType.TREND_CONTEXT));
    }

    @Override
    public CapabilityAvailability availability() {
        return CapabilityAvailability.AVAILABLE;
    }

    @Override
    public CapabilityAnalysisResult analyze(
            IntelligenceAnalysisRequest request, IntelligenceContext context) {
        return CapabilityAnalysisResult.empty("Trend Context is executed through the artifact capability boundary");
    }

    private TrendContextCapabilityContent content(
            TrendContextAssessmentInput input, TrendContextAssessment assessment) {
        Map<TrendContextRole, TrendContextSourceReference> references = new EnumMap<>(TrendContextRole.class);
        input.roleSeries().forEach((role, series) -> references.put(role, series.sourceReference()));
        String status = assessment.findings().isEmpty() ? "AVAILABLE" : "DEGRADED";
        return new TrendContextCapabilityContent(
                assessment, status, assessment.findings().stream()
                .map(value -> value.code() + ":" + value.message()).toList(),
                references, assessment.assessmentAt(), assessment.cutOffAt(),
                assessment.inputFingerprint(), assessment.assessmentFingerprint());
    }

    private String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName() : exception.getMessage();
    }

}
