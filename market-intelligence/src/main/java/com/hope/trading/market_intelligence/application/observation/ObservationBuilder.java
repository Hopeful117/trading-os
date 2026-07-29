package com.hope.trading.market_intelligence.application.observation;

import com.hope.trading.market_intelligence.application.port.*;
import com.hope.trading.market_intelligence.domain.artifact.*;
import com.hope.trading.market_intelligence.domain.capability.*;
import com.hope.trading.market_intelligence.domain.observation.*;

import java.time.Clock;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ObservationBuilder {
    private final CapabilityExecutionRepository executions;
    private final ObservationRepository observations;
    private final ObservationFactory factory;
    private final Clock clock;

    public ObservationBuilder(
            CapabilityExecutionRepository executions,
            ObservationRepository observations,
            ObservationFactory factory,
            Clock clock
    ) {
        this.executions = Objects.requireNonNull(executions);
        this.observations = Objects.requireNonNull(observations);
        this.factory = Objects.requireNonNull(factory);
        this.clock = Objects.requireNonNull(clock);
    }

    public Observation build(
            UUID analysisExecutionId,
            String instrument,
            ObservationConsolidationRule rule
    ) {
        List<CapabilityExecution> completed = executions
                .findByAnalysisExecutionId(analysisExecutionId).stream()
                .filter(execution -> execution.state() == CapabilityExecutionState.COMPLETED)
                .filter(execution -> execution.result()
                        .map(result -> result.completeness() == CapabilityCompleteness.COMPLETE)
                        .orElse(false))
                .toList();
        if (completed.isEmpty()) {
            throw new IllegalArgumentException("No complete capability result is available");
        }

        ObservationRuleResult result = Objects.requireNonNull(
                rule.evaluate(instrument, completed), "rule result");
        Map<UUID, CapabilityExecution> byId = completed.stream()
                .collect(Collectors.toUnmodifiableMap(CapabilityExecution::id, Function.identity()));
        List<ObservationEvidence> evidence = result.evidence().stream()
                .map(candidate -> evidence(candidate, byId, instrument))
                .toList();
        if (evidence.isEmpty()) throw new IllegalArgumentException("Rule produced no evidence");

        Observation current = observations.findByInstrument(instrument).stream()
                .filter(item -> item.type().equals(result.type()))
                .filter(item -> item.status() == ObservationStatus.ACTIVE)
                .max(Comparator.comparingLong(Observation::version))
                .orElse(null);
        Observation next = factory.create(
                current == null ? UUID.randomUUID() : current.lineageId(),
                current == null ? 1 : current.version() + 1,
                instrument, result.type(), result.title(), result.explanation(),
                result.categories(), result.horizon(), clock.instant(), result.validFrom(),
                result.validUntil(), current == null ? null : current.id(), rule.version(), evidence);

        if (current == null) {
            return observations.save(next);
        }
        Observation superseded = factory.superseded(current, next.id());
        observations.supersede(superseded, next);
        return next;
    }

    public Observation expire(UUID observationId) {
        Observation current = observations.findById(observationId)
                .orElseThrow(() -> new NoSuchElementException("Observation not found"));
        if (current.status() != ObservationStatus.ACTIVE) {
            throw new IllegalStateException("Only an active observation can expire");
        }
        return observations.save(factory.expired(current));
    }

    private ObservationEvidence evidence(
            ObservationEvidenceCandidate candidate,
            Map<UUID, CapabilityExecution> executions,
            String instrument
    ) {
        CapabilityExecution execution = Optional.ofNullable(
                        executions.get(candidate.capabilityExecutionId()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Evidence references an unavailable capability result"));
        CapabilityResult result = execution.result().orElseThrow();
        List<ArtifactTrace> artifacts = result.artifacts().stream()
                .map(ProducedArtifact::artifact)
                .map(artifact -> artifactTrace(artifact, execution.id(), instrument))
                .toList();
        CapabilityResultTrace trace = new CapabilityResultTrace(
                execution.id(), execution.capabilityId().value(),
                execution.capabilityVersion().value(), artifacts);
        return new ObservationEvidence(
                UUID.randomUUID(), execution.capabilityId().value(), candidate.title(),
                candidate.explanation(), candidate.measurements(), candidate.thresholds(),
                candidate.observedAt(), candidate.confidenceContribution(), trace);
    }

    private ArtifactTrace artifactTrace(
            StoredArtifact artifact, UUID capabilityExecutionId, String instrument
    ) {
        if (!capabilityExecutionId.equals(artifact.provenance().producingExecutionId())) {
            throw new IllegalArgumentException("Artifact provenance does not match capability result");
        }
        ArtifactCacheKey key = artifact.key();
        String tracedInstrument = Optional.ofNullable(key.scope().instrument()).orElse(instrument);
        String timeframe = Optional.ofNullable(key.scope().timeframe()).orElse("unspecified");
        RawMarketDataReference raw = new RawMarketDataReference(
                artifact.provenance().producerId(), tracedInstrument, timeframe,
                key.inputFingerprint().value(), artifact.provenance().producedAt());
        return new ArtifactTrace(
                key.identity(), key.parametersFingerprint().value(),
                key.inputFingerprint().value(), List.of(raw));
    }
}
