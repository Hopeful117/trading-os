package com.hope.trading.market_intelligence.domain.observation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class Observation {
    private final UUID id;
    private final UUID lineageId;
    private final long version;
    private final String instrument;
    private final ObservationType type;
    private final ObservationStatus status;
    private final String title;
    private final String explanation;
    private final Set<String> categories;
    private final String horizon;
    private final Instant createdAt;
    private final Instant validFrom;
    private final Instant validUntil;
    private final UUID supersedes;
    private final UUID supersededBy;
    private final String consolidationRuleVersion;
    private final List<ObservationEvidence> evidence;
    private final ObservationConfidence confidence;

    Observation(
            UUID id, UUID lineageId, long version, String instrument, ObservationType type,
            ObservationStatus status, String title, String explanation, Set<String> categories,
            String horizon, Instant createdAt, Instant validFrom, Instant validUntil,
            UUID supersedes, UUID supersededBy, String consolidationRuleVersion,
            List<ObservationEvidence> evidence, ObservationConfidence confidence
    ) {
        this.id = Objects.requireNonNull(id);
        this.lineageId = Objects.requireNonNull(lineageId);
        if (version < 1) throw new IllegalArgumentException("Version starts at 1");
        this.version = version;
        this.instrument = required(instrument, "instrument");
        this.type = Objects.requireNonNull(type);
        this.status = Objects.requireNonNull(status);
        this.title = required(title, "title");
        this.explanation = required(explanation, "explanation");
        this.categories = Set.copyOf(categories);
        this.horizon = required(horizon, "horizon");
        this.createdAt = Objects.requireNonNull(createdAt);
        this.validFrom = Objects.requireNonNull(validFrom);
        if (validUntil != null && validUntil.isBefore(validFrom)) {
            throw new IllegalArgumentException("validUntil cannot precede validFrom");
        }
        this.validUntil = validUntil;
        this.supersedes = supersedes;
        this.supersededBy = supersededBy;
        this.consolidationRuleVersion = required(
                consolidationRuleVersion, "consolidationRuleVersion");
        this.evidence = List.copyOf(evidence);
        if (this.evidence.isEmpty()) throw new IllegalArgumentException("Evidence is required");
        this.confidence = Objects.requireNonNull(confidence);
    }

    private static String required(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " is required");
        return normalized;
    }

    public UUID id() { return id; }
    public UUID lineageId() { return lineageId; }
    public long version() { return version; }
    public String instrument() { return instrument; }
    public ObservationType type() { return type; }
    public ObservationStatus status() { return status; }
    public String title() { return title; }
    public String explanation() { return explanation; }
    public Set<String> categories() { return categories; }
    public String horizon() { return horizon; }
    public Instant createdAt() { return createdAt; }
    public Instant validFrom() { return validFrom; }
    public Optional<Instant> validUntil() { return Optional.ofNullable(validUntil); }
    public Optional<UUID> supersedes() { return Optional.ofNullable(supersedes); }
    public Optional<UUID> supersededBy() { return Optional.ofNullable(supersededBy); }
    public String consolidationRuleVersion() { return consolidationRuleVersion; }
    public List<ObservationEvidence> evidence() { return evidence; }
    public ObservationConfidence confidence() { return confidence; }
}
