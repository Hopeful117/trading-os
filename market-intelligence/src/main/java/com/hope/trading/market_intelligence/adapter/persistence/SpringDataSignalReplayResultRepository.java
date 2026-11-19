package com.hope.trading.market_intelligence.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataSignalReplayResultRepository
        extends JpaRepository<JpaSignalReplayResultEntity, UUID> {
    Optional<JpaSignalReplayResultEntity> findByResultFingerprint(String resultFingerprint);
}
