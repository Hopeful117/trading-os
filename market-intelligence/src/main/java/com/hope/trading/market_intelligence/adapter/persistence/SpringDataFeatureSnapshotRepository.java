package com.hope.trading.market_intelligence.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

interface SpringDataFeatureSnapshotRepository extends JpaRepository<JpaFeatureSnapshotEntity, UUID> {
    Optional<JpaFeatureSnapshotEntity> findBySnapshotFingerprint(String snapshotFingerprint);

    Optional<JpaFeatureSnapshotEntity> findByIdentityFingerprint(String identityFingerprint);

    List<JpaFeatureSnapshotEntity> findByEvaluationCutoffGreaterThanEqualAndEvaluationCutoffLessThanAndProjectionVersionAndEvidencePolicy(
            Instant startCutoffInclusive, Instant endCutoffExclusive, String projectionVersion, String evidencePolicy);

    List<JpaFeatureSnapshotEntity> findByMarketIdInAndEvaluationCutoffGreaterThanEqualAndEvaluationCutoffLessThanAndProjectionVersionAndEvidencePolicy(
            Set<UUID> marketIds, Instant startCutoffInclusive, Instant endCutoffExclusive,
            String projectionVersion, String evidencePolicy);

    List<JpaFeatureSnapshotEntity> findByMarketIdInAndEvaluationCutoffGreaterThanEqualAndEvaluationCutoffLessThanAndProjectionVersionAndEvidencePolicy(
            Set<UUID> marketIds, Instant startCutoffInclusive, Instant endCutoffExclusive,
            String projectionVersion, String evidencePolicy, Pageable pageable);

    List<JpaFeatureSnapshotEntity> findByMarketIdInAndProjectionVersionAndEvidencePolicy(
            Set<UUID> marketIds, String projectionVersion, String evidencePolicy);

    long countByMarketIdInAndEvaluationCutoffGreaterThanAndProjectionVersionAndEvidencePolicy(
            Set<UUID> marketIds, Instant cutoffExclusive, String projectionVersion, String evidencePolicy);

    Optional<JpaFeatureSnapshotEntity> findTopByMarketIdAndEvaluationCutoffLessThanAndProjectionVersionAndEvidencePolicyOrderByEvaluationCutoffDesc(
            UUID marketId, Instant endCutoffExclusive, String projectionVersion, String evidencePolicy);
}
