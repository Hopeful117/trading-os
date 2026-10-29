package com.hope.trading.market_intelligence.application.capability;

import com.hope.trading.market_intelligence.domain.*;
import com.hope.trading.market_intelligence.domain.artifact.*;
import com.hope.trading.market_intelligence.domain.capability.*;
import com.hope.trading.market_intelligence.domain.execution.AnalysisResultQuality;
import com.hope.trading.market_intelligence.domain.tradeability.TradeabilityAssessment;
import com.hope.trading.market_intelligence.domain.tradeability.TradeabilityCalculator;
import com.hope.trading.market_intelligence.domain.tradeability.TradeabilityCapabilityContent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public final class TradeabilityAnalysisCapability
        implements DeterministicAnalysisCapability, Capability {
    public static final String CAPABILITY_ID = "market-tradeability-analysis";
    public static final String CAPABILITY_VERSION = "1.0.0";
    private final TradeabilityCalculator calculator;

    @Autowired
    public TradeabilityAnalysisCapability() {
        this(new TradeabilityCalculator());
    }

    TradeabilityAnalysisCapability(TradeabilityCalculator calculator) {
        this.calculator = calculator;
    }

    @Override
    public CapabilityMetadata metadata() {
        return new CapabilityMetadata(
                new CapabilityId(CAPABILITY_ID), new CapabilityVersion(CAPABILITY_VERSION),
                CapabilityCategory.DETERMINISTIC, ExecutionPolicy.ON_DEMAND,
                RetryPolicy.disabled(), List.of(
                        optional(ProductionArtifactTypes.MARKET_IDENTITY),
                        optional(ProductionArtifactTypes.MARKET_SNAPSHOT),
                        optional(ProductionArtifactTypes.ORDER_BOOK_REPLAY),
                        optional(ProductionArtifactTypes.OHLC_HISTORY),
                        optional(ProductionArtifactTypes.NEWS_CONTEXT)),
                List.of(new ProducedContribution.ArtifactContribution(
                        ProductionArtifactTypes.TRADEABILITY_ASSESSMENT,
                        ProductionArtifactTypes.V1, Set.of()),
                        new ProducedContribution.MetricContribution("blockingEvents")),
                Duration.ofSeconds(1), null);
    }

    private com.hope.trading.market_intelligence.domain.capability.ArtifactRequirement optional(ArtifactType type) {
        return new com.hope.trading.market_intelligence.domain.capability.ArtifactRequirement(type, ProductionArtifactTypes.V1,
                VersionCompatibilityMode.EXACT, false, ArtifactCardinality.ONE, false);
    }

    @Override
    public CapabilityResult execute(CapabilityContext context) {
        List<StoredArtifact> artifacts = context.resolvedArtifacts().values().stream()
                .flatMap(Collection::stream).toList();
        Instant cutoff = java.util.Objects.requireNonNull(context.evaluationCutoff(),
                "evaluationCutoff is required for deterministic tradeability execution");
        if (artifacts.isEmpty()
                || artifacts.stream().map(StoredArtifact::provenance)
                .anyMatch(provenance -> provenance.evidencePolicy() == null
                        || provenance.evidencePolicy() == EvidenceAcquisitionPolicy.LIVE_CURRENT
                        || !cutoff.equals(provenance.evaluationCutoff()))) {
            throw new IllegalStateException(
                    "Tradeability requires approved non-live evidence provenance at the execution cutoff");
        }
        UUID marketId = artifacts.stream().map(StoredArtifact::key)
                .map(ArtifactCacheKey::scope).map(ArtifactScope::marketId)
                .filter(java.util.Objects::nonNull).findFirst().orElse(UUID.nameUUIDFromBytes(
                        "tradeability-unknown-market".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        String instrument = artifacts.stream().map(StoredArtifact::content)
                .filter(MarketIdentityContext.class::isInstance).map(MarketIdentityContext.class::cast)
                .map(MarketIdentityContext::symbol).findFirst()
                .or(() -> artifacts.stream().map(StoredArtifact::content)
                        .filter(MarketSnapshotContext.class::isInstance)
                        .map(MarketSnapshotContext.class::cast)
                        .map(MarketSnapshotContext::symbol).findFirst())
                .orElseThrow(() -> new IllegalStateException(
                        "Canonical market instrument is unavailable for tradeability analysis"));
        TradeabilityCapabilityContent content = calculate(marketId, instrument, cutoff, artifacts);
        StoredArtifact output = output(context, content, marketId, instrument, cutoff, artifacts);
        long blocking = content.assessment().evidence().stream()
                .filter(value -> value.role() == TradeabilityAssessment.Role.BLOCKING).count();
        return new CapabilityResult(metadata().producedContributions(),
                List.of(new ProducedArtifact(ProductionArtifactTypes.TRADEABILITY_ASSESSMENT,
                        ProductionArtifactTypes.V1, output)),
                Map.of("blockingEvents", BigDecimal.valueOf(blocking)),
                content.diagnostics(), CapabilityCompleteness.COMPLETE);
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
                ContextRequirement.optionalPublic(ContextSectionType.MARKET_IDENTITY),
                ContextRequirement.optionalPublic(ContextSectionType.MARKET_SNAPSHOT),
                ContextRequirement.optionalPublic(ContextSectionType.ORDER_BOOK),
                ContextRequirement.optionalPublic(ContextSectionType.HISTORICAL_OHLC),
                ContextRequirement.optionalPublic(ContextSectionType.NEWS));
    }

    @Override
    public CapabilityAvailability availability() {
        return CapabilityAvailability.AVAILABLE;
    }

    @Override
    public CapabilityAnalysisResult analyze(
            IntelligenceAnalysisRequest request, IntelligenceContext context) {
        if (request.evidencePolicy() == EvidenceAcquisitionPolicy.LIVE_CURRENT) {
            return CapabilityAnalysisResult.empty(
                    "Tradeability requires an approved live order-book source; LIVE_CURRENT is unsupported");
        }
        Instant cutoff = context.evaluationCutoff().orElseThrow(
                () -> new IllegalStateException("evaluationCutoff is required for deterministic tradeability analysis"));
        if (request.evidencePolicy() == EvidenceAcquisitionPolicy.STRICT_OBSERVED
                && context.sections().values().stream()
                .filter(section -> section.payload() != null)
                .anyMatch(section -> section.provenance() == null
                        || section.provenance().evidencePolicy() != EvidenceAcquisitionPolicy.STRICT_OBSERVED
                        || !cutoff.equals(section.provenance().evaluationCutoff()))) {
            return CapabilityAnalysisResult.empty(
                    "Tradeability requires strict observed provenance at the execution cutoff");
        }
        String instrument = context.section(ContextSectionType.MARKET_IDENTITY)
                .map(ContextSection::payload).filter(MarketIdentityContext.class::isInstance)
                .map(MarketIdentityContext.class::cast).map(MarketIdentityContext::symbol)
                .or(() -> context.section(ContextSectionType.MARKET_SNAPSHOT)
                .map(ContextSection::payload).filter(MarketSnapshotContext.class::isInstance)
                        .map(MarketSnapshotContext.class::cast).map(MarketSnapshotContext::symbol))
                .orElseThrow(() -> new IllegalStateException(
                        "Canonical market instrument is unavailable for tradeability analysis"));
        TradeabilityAssessment assessment = calculator.calculate(request.marketId(), instrument,
                cutoff, context.section(ContextSectionType.MARKET_SNAPSHOT).orElse(null),
                context.section(ContextSectionType.ORDER_BOOK).orElse(null),
                context.section(ContextSectionType.HISTORICAL_OHLC).orElse(null),
                context.section(ContextSectionType.NEWS).orElse(null));
        long blocking = assessment.evidence().stream()
                .filter(value -> value.role() == TradeabilityAssessment.Role.BLOCKING).count();
        return new CapabilityAnalysisResult(List.of(new IntelligenceFinding(
                CAPABILITY_ID + ":" + request.marketId() + ":" + cutoff,
                CAPABILITY_ID, AnalysisOrigin.DETERMINISTIC, FindingType.DETERMINISTIC_FINDING,
                "Market tradeability evidence",
                "Structured execution-context evidence without an implicit suitability score.",
                Map.of("blockingEvents", BigDecimal.valueOf(blocking)), BigDecimal.ONE,
                Set.of(ContextSectionType.MARKET_SNAPSHOT, ContextSectionType.ORDER_BOOK,
                        ContextSectionType.HISTORICAL_OHLC, ContextSectionType.NEWS), cutoff)),
                assessment.evidence().stream().map(TradeabilityAssessment.Evidence::code).toList());
    }

    private TradeabilityCapabilityContent calculate(
            UUID marketId, String instrument, Instant cutoff, List<StoredArtifact> artifacts) {
        return new TradeabilityCapabilityContent(calculator.calculate(marketId, instrument, cutoff,
                section(artifacts, ProductionArtifactTypes.MARKET_SNAPSHOT, cutoff),
                section(artifacts, ProductionArtifactTypes.ORDER_BOOK_REPLAY, cutoff),
                section(artifacts, ProductionArtifactTypes.OHLC_HISTORY, cutoff),
                section(artifacts, ProductionArtifactTypes.NEWS_CONTEXT, cutoff)),
                "AVAILABLE", List.of(), cutoff.plus(Duration.ofMinutes(15)));
    }

    private ContextSection section(List<StoredArtifact> artifacts, ArtifactType type, Instant cutoff) {
        return artifacts.stream().filter(value -> value.key().identity().artifactType().equals(type.value()))
                .map(value -> section(value, type, cutoff))
                .findFirst().orElse(null);
    }

    private ContextSection section(StoredArtifact artifact, ArtifactType type, Instant cutoff) {
        ArtifactFreshness freshness = artifact.freshness();
        ContextSectionStatus status = freshness.producedAt().isAfter(cutoff)
                ? ContextSectionStatus.INCOMPLETE
                : freshness.validUntil().isBefore(cutoff)
                || freshness.invalidationTime().map(value -> !value.isAfter(cutoff)).orElse(false)
                ? ContextSectionStatus.STALE : ContextSectionStatus.AVAILABLE;
        ArtifactProvenance provenance = artifact.provenance();
        return new ContextSection(
                sectionType(type), status, ContextSensitivity.PUBLIC,
                status == ContextSectionStatus.INCOMPLETE ? null : (ContextPayload) artifact.content(),
                        new ContextProvenance(provenance.producerId(), provenance.producedAt(), freshness.producedAt(),
                         artifact.key().identity().artifactType(), provenance.producerVersion(), freshness.sourceVersion(),
                         provenance.evidencePolicy(), provenance.evidenceFingerprint(),
                         provenance.observationCount(),
                         provenance.evaluationCutoff() == null ? cutoff : provenance.evaluationCutoff()),
                status == ContextSectionStatus.AVAILABLE ? null : "Artifact is not eligible for the evaluation cutoff");
    }

    private ContextSectionType sectionType(ArtifactType type) {
        if (type == ProductionArtifactTypes.MARKET_SNAPSHOT) return ContextSectionType.MARKET_SNAPSHOT;
        if (type == ProductionArtifactTypes.ORDER_BOOK_REPLAY) return ContextSectionType.ORDER_BOOK;
        if (type == ProductionArtifactTypes.OHLC_HISTORY) return ContextSectionType.HISTORICAL_OHLC;
        return ContextSectionType.NEWS;
    }

    private StoredArtifact output(CapabilityContext context,
                                  TradeabilityCapabilityContent content,
                                  UUID marketId, String instrument, Instant cutoff,
                                  List<StoredArtifact> inputs) {
        ArtifactIdentity identity = new ArtifactIdentity(
                ProductionArtifactTypes.TRADEABILITY_ASSESSMENT.value(), CAPABILITY_ID, CAPABILITY_VERSION);
        ArtifactScope scope = ArtifactScope.publicMarket(marketId, "TRADEABILITY", AnalysisExecutionMode.ACTIVE);
        ArtifactFingerprint input = ArtifactFingerprint.ofInputs(inputs.stream()
                .map(value -> value.key().inputFingerprint().value()).sorted().toList());
        EvidenceAcquisitionPolicy policy = inputs.stream()
                .map(value -> value.provenance().evidencePolicy())
                .filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
        Integer observationCount = inputs.stream()
                .map(value -> value.provenance().observationCount())
                .filter(java.util.Objects::nonNull)
                .reduce(Integer::sum).orElse(null);
        StoredArtifact artifact = new StoredArtifact(
                new ArtifactCacheKey(identity, scope, ArtifactFingerprint.empty(), input), content,
                ArtifactFreshness.validUntil(cutoff, content.validUntil(), content.assessment().resultFingerprint()),
                new ArtifactProvenance(CAPABILITY_ID, CAPABILITY_VERSION,
                        context.capabilityExecutionId(), cutoff, inputs.stream()
                        .map(value -> value.key().identity()).collect(java.util.stream.Collectors.toSet()), Set.of(),
                        null, content.assessment().resultFingerprint(), policy, observationCount, cutoff)
                        .withEvidenceFingerprint(content.assessment().resultFingerprint()),
                AnalysisResultQuality.COMPLETE, ProductionArtifactTypes.V1);
        return artifact;
    }
}
