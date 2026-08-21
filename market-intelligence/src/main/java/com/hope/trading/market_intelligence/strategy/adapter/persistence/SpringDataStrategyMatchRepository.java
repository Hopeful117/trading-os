package com.hope.trading.market_intelligence.strategy.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.Table;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data access for the append-only strategy_matches fact table.
 */
public interface SpringDataStrategyMatchRepository
        extends JpaRepository<JpaStrategyMatchEntity, UUID> {

    List<JpaStrategyMatchEntity> findByStrategyIdAndStrategyVersionAndMarketIdAndAnalysisExecutionIdAndContextDigest(
            UUID strategyId, int strategyVersion, UUID marketId,
            UUID analysisExecutionId, String contextDigest);

    List<JpaStrategyMatchEntity> findByAnalysisExecutionId(UUID analysisExecutionId);

    default JpaStrategyMatchEntity required(UUID matchId) {
        return findById(matchId).orElseThrow(EntityNotFoundException::new);
    }
}
