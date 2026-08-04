package com.hope.trading.market_intelligence.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

interface SpringDataTradingOpportunityRepository
        extends JpaRepository<JpaTradingOpportunityEntity, JpaTradingOpportunityId> {
    List<JpaTradingOpportunityEntity> findByOpportunityIdOrderByVersionAsc(UUID opportunityId);
    Optional<JpaTradingOpportunityEntity> findFirstByOpportunityIdOrderByVersionDesc(UUID opportunityId);
}
