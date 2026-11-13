package com.hope.trading.market_intelligence.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataResearchHypothesisRepository
        extends JpaRepository<JpaResearchHypothesisEntity, JpaResearchHypothesisEntity.Identity> {
    boolean existsByHypothesisIdAndVersion(UUID hypothesisId, int version);
}
