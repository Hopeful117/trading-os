package com.hope.trading.market_intelligence.strategy.application;

import com.hope.trading.market_intelligence.strategy.domain.StrategyDefinition;
import com.hope.trading.market_intelligence.strategy.domain.StrategyId;

import java.util.List;
import java.util.Optional;

/**
 * Port for persisting and retrieving exact strategy versions.
 */
public interface StrategyDefinitionRepository {

    StrategyDefinition save(StrategyDefinition definition);

    Optional<StrategyDefinition> find(StrategyId strategyId, int version);

    List<StrategyDefinition> findAllVersions(StrategyId strategyId);

    /** All persisted strategy versions (runtime governance source of truth). */
    List<StrategyDefinition> findAll();
}
