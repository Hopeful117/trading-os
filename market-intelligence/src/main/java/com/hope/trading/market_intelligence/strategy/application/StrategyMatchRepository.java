package com.hope.trading.market_intelligence.strategy.application;

import com.hope.trading.market_intelligence.strategy.domain.StrategyMatch;
import com.hope.trading.market_intelligence.strategy.domain.StrategyMatchIdentity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port for append-only StrategyMatch persistence. Insert and read only: no
 * business update, no delete, no analytics aggregation.
 */
public interface StrategyMatchRepository {

    Optional<StrategyMatch> findById(UUID matchId);

    Optional<StrategyMatch> findByIdentity(StrategyMatchIdentity identity);

    List<StrategyMatch> findByAnalysisExecutionId(UUID analysisExecutionId);

    /** Insert-only. Duplicate logical identities are rejected by the database. */
    StrategyMatch save(StrategyMatch match);
}
