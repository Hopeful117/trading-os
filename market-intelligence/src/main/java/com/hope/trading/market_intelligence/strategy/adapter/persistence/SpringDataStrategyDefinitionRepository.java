package com.hope.trading.market_intelligence.strategy.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SpringDataStrategyDefinitionRepository
        extends JpaRepository<JpaStrategyDefinitionEntity, JpaStrategyDefinitionEntity.Pk> {

    List<JpaStrategyDefinitionEntity> findByStrategyIdOrderByVersionAsc(UUID strategyId);
}
