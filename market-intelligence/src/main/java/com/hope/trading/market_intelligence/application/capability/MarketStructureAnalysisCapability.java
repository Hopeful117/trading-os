package com.hope.trading.market_intelligence.application.capability;

import com.hope.trading.market_intelligence.adapter.marketdata.TrendContextRoleHistory;
import com.hope.trading.market_intelligence.domain.artifact.*;
import com.hope.trading.market_intelligence.domain.capability.*;
import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.marketstructure.*;
import com.hope.trading.market_intelligence.domain.trendcontext.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;

@Component
public final class MarketStructureAnalysisCapability implements Capability {
    public static final String CAPABILITY_ID = "market-structure-analysis";
    public static final String CAPABILITY_VERSION = "1.1.0";

    private final com.hope.trading.market_intelligence.adapter.marketdata.TrendContextInputMapper mapper;
    private final TrendContextProfile profile;

    public MarketStructureAnalysisCapability(
            com.hope.trading.market_intelligence.adapter.marketdata.TrendContextInputMapper mapper,
            TrendContextProfile profile) {
        this.mapper = mapper;
        this.profile = profile;
    }

    @Override
    public CapabilityMetadata metadata() {
        return new CapabilityMetadata(new CapabilityId(CAPABILITY_ID),
                new CapabilityVersion(CAPABILITY_VERSION), CapabilityCategory.DETERMINISTIC,
                ExecutionPolicy.ON_DEMAND, RetryPolicy.disabled(), List.of(
                new com.hope.trading.market_intelligence.domain.capability.ArtifactRequirement(ProductionArtifactTypes.TREND_CONTEXT_HISTORY,
                        ProductionArtifactTypes.V1, VersionCompatibilityMode.EXACT,
                        true, ArtifactCardinality.ONE, true)), List.of(
                new ProducedContribution.ArtifactContribution(ProductionArtifactTypes.MARKET_STRUCTURE,
                        ProductionArtifactTypes.MARKET_STRUCTURE_V2, Set.of())), Duration.ofSeconds(5), null);
    }

    @Override
    public CapabilityResult execute(CapabilityContext context) {
        Optional<StoredArtifact> input = context.resolvedArtifacts().values().stream()
                .flatMap(Collection::stream)
                .filter(value -> value.content() instanceof TrendContextRoleHistory).findFirst();
        if (input.isEmpty()) return degraded("Trend Context history is unavailable");
        TrendContextRoleHistory history = (TrendContextRoleHistory) input.get().content();
        TrendContextAssessmentInput mapped;
        try {
            mapped = mapper.map(history.responsesByRole(), profile, history.assessmentAt(),
                    history.cutOffAt(), history.ruleVersion());
        } catch (RuntimeException exception) {
            return degraded("Market Structure input rejected: " + safeMessage(exception));
        }
        List<ProducedArtifact> outputs = new ArrayList<>();
        for (TrendContextRole role : TrendContextRole.values()) {
            TrendContextRoleSeries series = mapped.roleSeries().get(role);
            if (series == null) continue;
            MarketStructureResult result = extract(mapped, series);
            StoredArtifact artifact = new StoredArtifact(
                            new ArtifactCacheKey(new ArtifactIdentity(ProductionArtifactTypes.MARKET_STRUCTURE.value(),
                            CAPABILITY_ID, CAPABILITY_VERSION),
                            ArtifactScope.publicMarket(mapped.marketId(), series.interval(), AnalysisExecutionMode.ACTIVE),
                            new ArtifactFingerprint(result.parameterFingerprint()),
                            new ArtifactFingerprint(result.inputFingerprint())),
                    new MarketStructureArtifactContent(result, result.cutOffAt(), result.inputFingerprint()),
                    ArtifactFreshness.validUntil(result.cutOffAt(),
                            result.cutOffAt().plus(Duration.ofMinutes(15)), result.inputFingerprint()),
                    new ArtifactProvenance(CAPABILITY_ID, CAPABILITY_VERSION,
                            context.capabilityExecutionId(), result.cutOffAt(),
                            Set.of(input.get().key().identity()), Set.of()),
                    result.availability() == MarketStructureAvailability.AVAILABLE
                            ? com.hope.trading.market_intelligence.domain.execution.AnalysisResultQuality.COMPLETE
                            : com.hope.trading.market_intelligence.domain.execution.AnalysisResultQuality.DEGRADED);
            outputs.add(new ProducedArtifact(ProductionArtifactTypes.MARKET_STRUCTURE,
                    ProductionArtifactTypes.MARKET_STRUCTURE_V2, artifact));
        }
        if (outputs.isEmpty()) return degraded("No structural interval evidence is available");
        boolean complete = outputs.stream().map(value -> (MarketStructureArtifactContent)
                value.artifact().content()).allMatch(value ->
                value.result().availability() == MarketStructureAvailability.AVAILABLE);
        return new CapabilityResult(metadata().producedContributions(), outputs,
                Map.of("structures", BigDecimal.valueOf(outputs.size())),
                outputs.stream().flatMap(value -> ((MarketStructureArtifactContent) value.artifact().content())
                        .result().findings().stream()).toList(),
                complete ? CapabilityCompleteness.COMPLETE : CapabilityCompleteness.DEGRADED);
    }

    private MarketStructureResult extract(TrendContextAssessmentInput input, TrendContextRoleSeries series) {
        List<TrendContextCandle> candles = series.candles();
        MarketStructureInput structureInput = new MarketStructureInput(
                input.marketId(), input.provider(), input.symbol(), series.interval(),
                candles.stream().map(c -> new MarketStructureCandle(c.openTime(), c.closeTime(), c.high(), c.low(),
                        c.sourceId(), c.sourceOccurredAt(), c.fetchedAt(), c.closed(), c.synthetic())).toList(),
                series.gapFindings().stream().map(g -> new MarketStructureGap(g.from(), g.to())).toList(),
                input.cutOffAt(), "CONFIRMED_SWING_V1", input.ruleVersion(), input.profile().profileId(),
                input.profile().profileVersion(), parameterFingerprint(input.profile()), input.fingerprint(),
                candles.stream().filter(c -> c.closed() && !c.synthetic()
                                && !c.closeTime().isAfter(input.cutOffAt())).count()
                        < input.profile().pivotRadius() * 2L + 1
                        ? MarketStructureEvidenceStatus.INSUFFICIENT
                        : MarketStructureEvidenceStatus.COMPLETE,
                series.exclusionFindings(), input.profile().pivotRadius(), input.profile().minimumSeparationBars());
        return new MarketStructureEngine().extract(structureInput);
    }

    private String parameterFingerprint(TrendContextProfile value) {
        return value.profileId() + ":" + value.profileVersion() + ":"
                + value.pivotRadius() + ":" + value.minimumSeparationBars();
    }

    private CapabilityResult degraded(String message) {
        return new CapabilityResult(List.of(), List.of(), Map.of(), List.of(message),
                CapabilityCompleteness.DEGRADED);
    }

    private String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
