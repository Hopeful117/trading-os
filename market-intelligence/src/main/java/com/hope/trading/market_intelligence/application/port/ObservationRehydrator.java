package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.domain.observation.*;

import java.time.Instant;
import java.util.*;

public interface ObservationRehydrator {
    Observation restore(Snapshot snapshot);

    record Snapshot(
            UUID id, UUID lineageId, long version, String instrument, ObservationType type,
            ObservationStatus status, String title, String explanation, Set<String> categories,
            String horizon, Instant createdAt, Instant validFrom, Instant validUntil,
            UUID supersedes, UUID supersededBy, String ruleVersion,
            List<ObservationEvidence> evidence) { }
}
