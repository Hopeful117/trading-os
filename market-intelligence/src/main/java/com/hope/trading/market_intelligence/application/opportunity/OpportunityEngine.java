package com.hope.trading.market_intelligence.application.opportunity;

import com.hope.trading.market_intelligence.application.port.*;
import com.hope.trading.market_intelligence.domain.observation.*;
import com.hope.trading.market_intelligence.domain.opportunity.*;

import java.time.Clock;
import java.util.*;

/** Sole application entry point authorized to create Opportunity aggregate versions. */
public final class OpportunityEngine {
    private final ObservationRepository observations;
    private final AiAnalysisCatalog aiAnalyses;
    private final TradingOpportunityRepository opportunities;
    private final OpportunityFusionPolicy fusion;
    private final OpportunityDeduplicationPolicy deduplication;
    private final OpportunityLifecyclePolicy lifecycle;
    private final OpportunityBuilder builder;
    private final OpportunityIdentifierGenerator identifiers;
    private final Clock clock;

    public OpportunityEngine(
            ObservationRepository observations, AiAnalysisCatalog aiAnalyses,
            TradingOpportunityRepository opportunities, OpportunityFusionPolicy fusion,
            OpportunityDeduplicationPolicy deduplication,
            OpportunityLifecyclePolicy lifecycle, OpportunityFactory factory,
            OpportunityIdentifierGenerator identifiers, Clock clock
    ) {
        this.observations = Objects.requireNonNull(observations);
        this.aiAnalyses = Objects.requireNonNull(aiAnalyses);
        this.opportunities = Objects.requireNonNull(opportunities);
        this.fusion = Objects.requireNonNull(fusion);
        this.deduplication = Objects.requireNonNull(deduplication);
        this.lifecycle = Objects.requireNonNull(lifecycle);
        this.builder = new OpportunityBuilder(Objects.requireNonNull(factory));
        this.identifiers = Objects.requireNonNull(identifiers);
        this.clock = Objects.requireNonNull(clock);
    }

    public synchronized OpportunityCreationResult create(CreateOpportunityCommand command) {
        Objects.requireNonNull(command);
        List<Observation> loaded = command.observations().stream()
                .map(reference -> observations.findById(reference.observationId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Unknown Observation: " + reference.observationId())))
                .toList();
        if (loaded.stream().anyMatch(item -> item.status() != ObservationStatus.ACTIVE)) {
            throw new IllegalArgumentException("Only active Observations can support an Opportunity");
        }
        if (loaded.stream().anyMatch(
                item -> !item.instrument().equalsIgnoreCase(command.instrument()))) {
            throw new IllegalArgumentException("Observation instrument mismatch");
        }
        if (!aiAnalyses.allExist(command.aiAnalyses())) {
            throw new IllegalArgumentException("Unknown AI Analysis reference");
        }

        OpportunityFusionResult fused = fusion.fuse(command, loaded, command.aiAnalyses());
        OpportunityIdentity identity = new OpportunityIdentity(
                command.instrument(), command.direction(), command.scenario(),
                command.timeframe(), fused.observations());
        TradingOpportunity equivalent = opportunities.findEquivalentCandidates(
                        command.instrument(), command.direction(), command.scenario(),
                        command.timeframe(),
                        command.evaluatedAt().minus(deduplication.equivalenceWindow())).stream()
                .filter(item -> deduplication.equivalent(identity, item, command.evaluatedAt()))
                .max(Comparator.comparing(item -> item.version().value()))
                .orElse(null);
        if (equivalent == null) {
            TradingOpportunity created = builder.create(
                    identifiers.next(), command, fused, clock.instant());
            return new OpportunityCreationResult.Created(opportunities.append(created));
        }
        TradingOpportunity latest = opportunities.findLatest(equivalent.id()).orElseThrow();
        TradingOpportunity next = builder.nextVersion(latest, command, fused, clock.instant());
        return new OpportunityCreationResult.VersionCreated(opportunities.append(next));
    }

    public TradingOpportunity transition(OpportunityId id, OpportunityStatus target) {
        TradingOpportunity current = opportunities.findLatest(id)
                .orElseThrow(() -> new NoSuchElementException("Opportunity not found"));
        lifecycle.validate(current.status(), target);
        return opportunities.append(builder.transition(current, target, clock.instant()));
    }
}
