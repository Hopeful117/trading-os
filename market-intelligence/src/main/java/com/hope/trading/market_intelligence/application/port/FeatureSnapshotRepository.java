package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.domain.feature.FeatureSnapshot;

import java.util.Optional;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface FeatureSnapshotRepository {
    Optional<FeatureSnapshot> findByFingerprint(String fingerprint);

    List<FeatureSnapshot> findByPopulation(Set<UUID> marketIds, Instant startCutoffInclusive, Instant endCutoffExclusive,
                                           String featureProjectionVersion, String evidencePolicy);

    List<FeatureSnapshot> findByPopulation(Set<UUID> marketIds, Instant startCutoffInclusive, Instant endCutoffExclusive,
                                           String featureProjectionVersion, String evidencePolicy, int limit);

    List<FeatureSnapshot> findByMarkets(Set<UUID> marketIds, String featureProjectionVersion, String evidencePolicy);

    long countByMarketsAfter(Set<UUID> marketIds, Instant cutoffExclusive, String featureProjectionVersion,
                             String evidencePolicy);

    Optional<FeatureSnapshot> findLatestByMarket(UUID marketId, Instant endCutoffExclusive,
                                                  String featureProjectionVersion, String evidencePolicy);

    FeatureSnapshot saveIfAbsent(FeatureSnapshot snapshot);

    default FeatureSnapshot saveIfAbsentOwned(FeatureSnapshot snapshot, UUID leaseId) {
        return saveIfAbsent(snapshot);
    }
}
