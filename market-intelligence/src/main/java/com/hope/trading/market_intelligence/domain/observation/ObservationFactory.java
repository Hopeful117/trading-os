package com.hope.trading.market_intelligence.domain.observation;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Domain construction boundary. Package-private creation methods deliberately
 * make {@link ObservationBuilder} the only production caller.
 */
public final class ObservationFactory {
    public Observation create(CreateValues values) {
        return new Observation(
                UUID.randomUUID(), values.lineageId(), values.version(), values.instrument(),
                values.type(), ObservationStatus.ACTIVE, values.title(), values.explanation(),
                values.categories(), values.horizon(), values.createdAt(), values.validFrom(),
                values.validUntil(), values.supersedes(), null, values.ruleVersion(),
                List.copyOf(values.evidence()), ObservationConfidence.from(values.evidence()),
                values.payload());
    }

    @SuppressWarnings("java:S107")
    public Observation create(UUID lineageId, long version, String instrument, ObservationType type,
            String title, String explanation, Set<String> categories, String horizon,
            Instant createdAt, Instant validFrom, Instant validUntil, UUID supersedes,
            String ruleVersion, List<ObservationEvidence> evidence) {
        return create(new CreateValues(lineageId, version, instrument, type, title, explanation,
                categories, horizon, createdAt, validFrom, validUntil, supersedes, ruleVersion,
                evidence, null));
    }

    @SuppressWarnings("java:S107")
    public Observation create(UUID lineageId, long version, String instrument, ObservationType type,
            String title, String explanation, Set<String> categories, String horizon,
            Instant createdAt, Instant validFrom, Instant validUntil, UUID supersedes,
            String ruleVersion, List<ObservationEvidence> evidence, ObservationPayload payload) {
        return create(new CreateValues(lineageId, version, instrument, type, title, explanation,
                categories, horizon, createdAt, validFrom, validUntil, supersedes, ruleVersion,
                evidence, payload));
    }

    public record CreateValues(UUID lineageId, long version, String instrument, ObservationType type,
            String title, String explanation, Set<String> categories, String horizon,
            Instant createdAt, Instant validFrom, Instant validUntil, UUID supersedes,
            String ruleVersion, List<ObservationEvidence> evidence, ObservationPayload payload) { }

    public Observation superseded(Observation current, UUID supersededBy) {
        return new Observation(
                current.id(), current.lineageId(), current.version(), current.instrument(),
                current.type(), ObservationStatus.SUPERSEDED, current.title(),
                current.explanation(), current.categories(), current.horizon(),
                current.createdAt(), current.validFrom(), current.validUntil().orElse(null),
                current.supersedes().orElse(null), supersededBy,
                current.consolidationRuleVersion(), current.evidence(), current.confidence(),
                current.payload().orElse(null));
    }

    public Observation expired(Observation current) {
        return new Observation(
                current.id(), current.lineageId(), current.version(), current.instrument(),
                current.type(), ObservationStatus.EXPIRED, current.title(),
                current.explanation(), current.categories(), current.horizon(),
                current.createdAt(), current.validFrom(), current.validUntil().orElse(null),
                current.supersedes().orElse(null), current.supersededBy().orElse(null),
                current.consolidationRuleVersion(), current.evidence(), current.confidence(),
                current.payload().orElse(null));
    }

    public Observation restore(RestoreValues values) {
        List<ObservationEvidence> copy = List.copyOf(values.evidence());
        return new Observation(
                values.id(), values.lineageId(), values.version(), values.instrument(), values.type(),
                values.status(), values.title(), values.explanation(), values.categories(), values.horizon(),
                values.createdAt(), values.validFrom(), values.validUntil(), values.supersedes(),
                values.supersededBy(), values.ruleVersion(), copy, ObservationConfidence.from(copy),
                values.payload());
    }

    public record RestoreValues(UUID id, UUID lineageId, long version, String instrument,
            ObservationType type, ObservationStatus status, String title, String explanation,
            Set<String> categories, String horizon, Instant createdAt, Instant validFrom,
            Instant validUntil, UUID supersedes, UUID supersededBy, String ruleVersion,
            List<ObservationEvidence> evidence, ObservationPayload payload) { }
}
