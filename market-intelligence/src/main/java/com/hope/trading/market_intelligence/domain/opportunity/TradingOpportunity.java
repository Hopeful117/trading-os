package com.hope.trading.market_intelligence.domain.opportunity;

import java.time.Instant;
import java.util.*;

public final class TradingOpportunity {
    private final OpportunityId id;
    private final OpportunityVersion version;
    private final OpportunityStatus status;
    private final String instrument;
    private final OpportunityDirection direction;
    private final String scenario;
    private final String timeframe;
    private final OpportunityType type;
    private final OpportunityOrigin origin;
    private final OpportunityScore score;
    private final String explanation;
    private final Set<ObservationReference> observations;
    private final Set<AiAnalysisReference> aiAnalyses;
    private final Instant evaluatedAt;
    private final Instant validFrom;
    private final Instant validUntil;
    private final Instant createdAt;
    /** Authoritative setup provenance (ADR-034). Null only for pre-0012 rows. */
    private final UUID strategyMatchId;

    TradingOpportunity(
            OpportunityId id, OpportunityVersion version, OpportunityStatus status,
            String instrument, OpportunityDirection direction, String scenario,
            String timeframe, OpportunityType type, OpportunityOrigin origin,
            OpportunityScore score, String explanation,
            Set<ObservationReference> observations, Set<AiAnalysisReference> aiAnalyses,
            Instant evaluatedAt, Instant validFrom, Instant validUntil, Instant createdAt,
            UUID strategyMatchId
    ) {
        this.id = Objects.requireNonNull(id);
        this.version = Objects.requireNonNull(version);
        this.status = Objects.requireNonNull(status);
        this.instrument = required(instrument, "instrument");
        this.direction = Objects.requireNonNull(direction);
        this.scenario = required(scenario, "scenario");
        this.timeframe = required(timeframe, "timeframe");
        this.type = Objects.requireNonNull(type);
        this.origin = Objects.requireNonNull(origin);
        this.score = Objects.requireNonNull(score);
        this.explanation = required(explanation, "explanation");
        this.observations = Set.copyOf(observations);
        if (this.observations.isEmpty()) {
            throw new IllegalArgumentException("At least one Observation is required");
        }
        this.aiAnalyses = Set.copyOf(aiAnalyses);
        this.evaluatedAt = Objects.requireNonNull(evaluatedAt);
        this.validFrom = Objects.requireNonNull(validFrom);
        if (validUntil != null && !validUntil.isAfter(validFrom)) {
            throw new IllegalArgumentException("validUntil must be after validFrom");
        }
        this.validUntil = validUntil;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.strategyMatchId = strategyMatchId;
    }

    private static String required(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " is required");
        return normalized;
    }

    public OpportunityId id() { return id; }
    public OpportunityVersion version() { return version; }
    public OpportunityStatus status() { return status; }
    public String instrument() { return instrument; }
    public OpportunityDirection direction() { return direction; }
    public String scenario() { return scenario; }
    public String timeframe() { return timeframe; }
    public OpportunityType type() { return type; }
    public OpportunityOrigin origin() { return origin; }
    public OpportunityScore score() { return score; }
    public String explanation() { return explanation; }
    public Set<ObservationReference> observations() { return observations; }
    public Set<AiAnalysisReference> aiAnalyses() { return aiAnalyses; }
    public Instant evaluatedAt() { return evaluatedAt; }
    public Instant validFrom() { return validFrom; }
    public Optional<Instant> validUntil() { return Optional.ofNullable(validUntil); }
    public Instant createdAt() { return createdAt; }

    public Optional<UUID> strategyMatchId() { return Optional.ofNullable(strategyMatchId); }
}
