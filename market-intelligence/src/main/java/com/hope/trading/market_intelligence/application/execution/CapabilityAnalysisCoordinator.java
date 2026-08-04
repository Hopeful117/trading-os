package com.hope.trading.market_intelligence.application.execution;

import com.hope.trading.market_intelligence.application.capability.ProductionArtifactTypes;
import com.hope.trading.market_intelligence.application.context.IntelligenceContextAssembler;
import com.hope.trading.market_intelligence.application.planning.ExecutionPlanner;
import com.hope.trading.market_intelligence.application.planning.PlanningRequest;
import com.hope.trading.market_intelligence.application.port.ArtifactPersistencePort;
import com.hope.trading.market_intelligence.application.strategy.AnalysisExecutionPlan;
import com.hope.trading.market_intelligence.application.strategy.AnalysisStrategyRegistry;
import com.hope.trading.market_intelligence.domain.*;
import com.hope.trading.market_intelligence.domain.artifact.*;
import com.hope.trading.market_intelligence.domain.capability.*;
import com.hope.trading.market_intelligence.domain.planning.ArtifactDescriptor;
import com.hope.trading.market_intelligence.domain.execution.AnalysisResultQuality;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Authoritative production bridge from the public analysis lifecycle to ADR-023. */
@Service
public class CapabilityAnalysisCoordinator {
    private final AnalysisStrategyRegistry strategies;
    private final IntelligenceContextAssembler contexts;
    private final ArtifactPersistencePort artifacts;
    private final ExecutionPlanner planner;
    private final ExecutionEngine engine;
    private final Clock clock;
    private final Map<UUID, ExecutionControl> controls = new ConcurrentHashMap<>();

    public CapabilityAnalysisCoordinator(
            AnalysisStrategyRegistry strategies,
            IntelligenceContextAssembler contexts,
            ArtifactPersistencePort artifacts,
            ExecutionPlanner planner,
            ExecutionEngine engine,
            Clock clock
    ) {
        this.strategies = strategies;
        this.contexts = contexts;
        this.artifacts = artifacts;
        this.planner = planner;
        this.engine = engine;
        this.clock = clock;
    }

    public ConsolidatedIntelligence analyze(
            UUID analysisExecutionId, IntelligenceAnalysisRequest request) {
        Instant startedAt = clock.instant();
        AnalysisExecutionPlan strategy = strategies.strategy(request.mode()).plan(request);
        List<ContextRequirement> requirements = requirements(strategy.capabilityIds());
        IntelligenceContext context = contexts.assemble(request, requirements);
        Set<ArtifactDescriptor> descriptors = materializeInitialArtifacts(
                analysisExecutionId, request, context);
        Set<CapabilityId> selected = strategy.capabilityIds().stream()
                .limit(strategy.maximumCapabilities())
                .map(CapabilityId::new)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        ExecutionControl control = new ExecutionControl();
        controls.put(analysisExecutionId, control);
        try {
            ExecutionSummary summary = engine.execute(
                    planner.plan(new PlanningRequest(
                            analysisExecutionId, selected, Set.of(), descriptors)), control);
            return consolidate(request, context, summary, startedAt, strategy.timeout());
        } finally {
            controls.remove(analysisExecutionId);
        }
    }

    public void cancel(UUID analysisExecutionId) {
        ExecutionControl control = controls.get(analysisExecutionId);
        if (control != null) {
            control.requestCancellation(new CancellationRequest(
                    clock.instant(), "analysis-execution", "Public analysis cancelled",
                    CancellationSource.USER, analysisExecutionId.toString()));
        }
    }

    private List<ContextRequirement> requirements(List<String> capabilities) {
        List<ContextRequirement> result = new ArrayList<>();
        if (capabilities.contains("spread-analysis")) {
            result.add(ContextRequirement.requiredPublic(ContextSectionType.MARKET_SNAPSHOT));
        }
        if (capabilities.contains("ohlc-range-analysis")) {
            result.add(ContextRequirement.requiredPublic(ContextSectionType.HISTORICAL_OHLC));
        }
        return result;
    }

    private Set<ArtifactDescriptor> materializeInitialArtifacts(
            UUID executionId, IntelligenceAnalysisRequest request, IntelligenceContext context) {
        Set<ArtifactDescriptor> result = new HashSet<>();
        context.sections().values().stream()
                .filter(section -> section.status() == ContextSectionStatus.AVAILABLE
                        || section.status() == ContextSectionStatus.STALE)
                .filter(section -> section.payload() != null)
                .forEach(section -> {
                    ArtifactType type = switch (section.type()) {
                        case MARKET_SNAPSHOT -> ProductionArtifactTypes.MARKET_SNAPSHOT;
                        case HISTORICAL_OHLC -> ProductionArtifactTypes.OHLC_HISTORY;
                        default -> null;
                    };
                    if (type == null) return;
                    Instant occurredAt = section.provenance().sourceOccurredAt();
                    Instant producedAt = occurredAt == null
                            ? section.provenance().fetchedAt() : occurredAt;
                    String sourceVersion = sourceVersion(section);
                    ArtifactScope scope = ArtifactScope.publicMarket(
                            request.marketId(), timeframe(section), request.mode());
                    ArtifactFingerprint inputFingerprint = ArtifactFingerprint.ofInputs(
                            List.of(request.marketId() + ":" + sourceVersion + ":" + section.payload()));
                    StoredArtifact stored = new StoredArtifact(
                            new ArtifactCacheKey(
                                    new ArtifactIdentity(type.value(), "market-data", sourceVersion),
                                    scope, ArtifactFingerprint.empty(), inputFingerprint),
                            section.payload(),
                            ArtifactFreshness.validUntil(
                                    producedAt, producedAt.plus(Duration.ofMinutes(15)), sourceVersion),
                            new ArtifactProvenance(
                                    "market-data", sourceVersion, null, producedAt, Set.of(), Set.of()),
                            section.status() == ContextSectionStatus.STALE
                                    ? AnalysisResultQuality.DEGRADED
                                    : AnalysisResultQuality.COMPLETE);
                    artifacts.save(executionId, new ProducedArtifact(
                            type, ProductionArtifactTypes.V1, stored));
                    result.add(new ArtifactDescriptor(type, ProductionArtifactTypes.V1));
                });
        return Set.copyOf(result);
    }

    private ConsolidatedIntelligence consolidate(
            IntelligenceAnalysisRequest request, IntelligenceContext context,
            ExecutionSummary summary, Instant startedAt, Duration timeout) {
        Instant completedAt = clock.instant();
        List<com.hope.trading.market_intelligence.domain.capability.CapabilityExecution> terminal =
                summary.attempts().stream().filter(item -> item.state().isTerminal()).toList();
        List<IntelligenceFinding> findings = terminal.stream()
                .filter(item -> item.state() == CapabilityExecutionState.COMPLETED)
                .flatMap(item -> item.result().stream())
                .filter(item -> item.completeness() == CapabilityCompleteness.COMPLETE)
                .filter(item -> !item.metrics().isEmpty())
                .map(result -> finding(request, result, completedAt))
                .toList();
        List<String> warnings = new ArrayList<>();
        context.sections().values().stream()
                .filter(item -> item.status() != ContextSectionStatus.AVAILABLE)
                .map(ContextSection::message).filter(Objects::nonNull).forEach(warnings::add);
        terminal.stream().filter(item -> item.state() != CapabilityExecutionState.COMPLETED)
                .map(item -> item.capabilityId().value() + " ended " + item.state())
                .forEach(warnings::add);
        IntelligenceExecutionStatus status;
        if (terminal.isEmpty() || terminal.stream().allMatch(
                item -> item.state() != CapabilityExecutionState.COMPLETED)) {
            status = IntelligenceExecutionStatus.FAILED;
        } else if (terminal.stream().anyMatch(
                item -> item.state() != CapabilityExecutionState.COMPLETED)) {
            status = IntelligenceExecutionStatus.PARTIAL;
        } else if (!warnings.isEmpty()) {
            status = IntelligenceExecutionStatus.DEGRADED;
        } else {
            status = IntelligenceExecutionStatus.COMPLETE;
        }
        List<ContextSectionSummary> sections = context.sections().values().stream()
                .map(this::summary).toList();
        List<com.hope.trading.market_intelligence.domain.CapabilityExecution> executions = terminal.stream()
                .map(item -> new com.hope.trading.market_intelligence.domain.CapabilityExecution(
                        item.capabilityId().value(), AnalysisOrigin.DETERMINISTIC,
                        legacyState(item.state()), duration(item),
                        item.failure().map(value -> value.errorCode()).orElse(null)))
                .toList();
        return new ConsolidatedIntelligence(
                request.analysisId(), request.marketId(), request.mode(), status,
                sections, findings, warnings,
                new AnalysisExecutionMetadata(
                        request.analysisId(), request.mode(), startedAt, completedAt,
                        Duration.between(startedAt, completedAt), timeout, executions));
    }

    private IntelligenceFinding finding(
            IntelligenceAnalysisRequest request, CapabilityResult result, Instant generatedAt) {
        DeterministicMeasurements details = result.artifacts().stream()
                .map(ProducedArtifact::artifact).map(StoredArtifact::content)
                .filter(DeterministicMeasurements.class::isInstance)
                .map(DeterministicMeasurements.class::cast).findFirst().orElseThrow();
        String capabilityId = result.artifacts().getFirst().artifact().provenance().producerId();
        return new IntelligenceFinding(
                capabilityId + ":" + request.marketId() + ":" + details.observedAt(),
                capabilityId, AnalysisOrigin.DETERMINISTIC,
                FindingType.DETERMINISTIC_FINDING, details.title(), details.explanation(),
                result.metrics(), BigDecimal.ONE, Set.of(), generatedAt);
    }

    private ContextSectionSummary summary(ContextSection section) {
        ContextProvenance provenance = section.provenance();
        return new ContextSectionSummary(
                section.type(), section.status(), section.sensitivity(),
                provenance == null ? null : provenance.source(),
                provenance == null ? null : provenance.sourceOccurredAt(), section.message());
    }

    private String sourceVersion(ContextSection section) {
        ContextProvenance provenance = section.provenance();
        Instant sourceAt = provenance.sourceOccurredAt() == null
                ? provenance.fetchedAt() : provenance.sourceOccurredAt();
        return provenance.source() + ":" + sourceAt;
    }

    private String timeframe(ContextSection section) {
        return section.payload() instanceof HistoricalOhlcContext ohlc
                ? ohlc.interval() : null;
    }

    private CapabilityExecutionStatus legacyState(CapabilityExecutionState state) {
        return switch (state) {
            case COMPLETED -> CapabilityExecutionStatus.COMPLETED;
            case SKIPPED -> CapabilityExecutionStatus.SKIPPED;
            case TIMED_OUT -> CapabilityExecutionStatus.TIMED_OUT;
            case FAILED, CANCELLED -> CapabilityExecutionStatus.FAILED;
            default -> throw new IllegalArgumentException("Execution is not terminal");
        };
    }

    private Duration duration(
            com.hope.trading.market_intelligence.domain.capability.CapabilityExecution execution) {
        return execution.startedAt().map(start -> Duration.between(
                start, execution.completedAt().orElse(start))).orElse(Duration.ZERO);
    }
}
