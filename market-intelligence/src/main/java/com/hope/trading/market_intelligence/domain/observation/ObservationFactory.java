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
    public Observation create(
            UUID lineageId, long version, String instrument, ObservationType type,
            String title, String explanation, Set<String> categories, String horizon,
            Instant createdAt, Instant validFrom, Instant validUntil, UUID supersedes,
            String ruleVersion, List<ObservationEvidence> evidence
    ) {
        return create(lineageId, version, instrument, type, title, explanation, categories,
                horizon, createdAt, validFrom, validUntil, supersedes, ruleVersion, evidence, null);
    }

    public Observation create(
            UUID lineageId, long version, String instrument, ObservationType type,
            String title, String explanation, Set<String> categories, String horizon,
            Instant createdAt, Instant validFrom, Instant validUntil, UUID supersedes,
            String ruleVersion, List<ObservationEvidence> evidence, ObservationPayload payload
    ) {
        List<ObservationEvidence> copy = List.copyOf(evidence);
        return new Observation(
                UUID.randomUUID(), lineageId, version, instrument, type, ObservationStatus.ACTIVE,
                title, explanation, categories, horizon, createdAt, validFrom, validUntil,
                supersedes, null, ruleVersion, copy, ObservationConfidence.from(copy), payload);
    }

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

    public Observation restore(
            UUID id, UUID lineageId, long version, String instrument, ObservationType type,
            ObservationStatus status, String title, String explanation, Set<String> categories,
            String horizon, Instant createdAt, Instant validFrom, Instant validUntil,
            UUID supersedes, UUID supersededBy, String ruleVersion,
            List<ObservationEvidence> evidence) {
        return restore(id, lineageId, version, instrument, type, status, title, explanation,
                categories, horizon, createdAt, validFrom, validUntil, supersedes,
                supersededBy, ruleVersion, evidence, null);
    }

    public Observation restore(
            UUID id, UUID lineageId, long version, String instrument, ObservationType type,
            ObservationStatus status, String title, String explanation, Set<String> categories,
            String horizon, Instant createdAt, Instant validFrom, Instant validUntil,
            UUID supersedes, UUID supersededBy, String ruleVersion,
            List<ObservationEvidence> evidence, ObservationPayload payload) {
        List<ObservationEvidence> copy = List.copyOf(evidence);
        return new Observation(
                id, lineageId, version, instrument, type, status, title, explanation,
                categories, horizon, createdAt, validFrom, validUntil, supersedes,
                supersededBy, ruleVersion, copy, ObservationConfidence.from(copy), payload);
    }
}
