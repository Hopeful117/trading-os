package com.hope.trading.market_intelligence.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataFeatureSnapshotRepository extends JpaRepository<JpaFeatureSnapshotEntity, UUID> {
    Optional<JpaFeatureSnapshotEntity> findBySnapshotFingerprint(String snapshotFingerprint);

    Optional<JpaFeatureSnapshotEntity> findByIdentityFingerprint(String identityFingerprint);
}
