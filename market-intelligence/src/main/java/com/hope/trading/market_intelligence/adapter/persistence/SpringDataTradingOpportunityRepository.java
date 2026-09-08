package com.hope.trading.market_intelligence.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.*;

interface SpringDataTradingOpportunityRepository
        extends JpaRepository<JpaTradingOpportunityEntity, JpaTradingOpportunityId> {
    List<JpaTradingOpportunityEntity> findByOpportunityIdOrderByVersionAsc(UUID opportunityId);
    Optional<JpaTradingOpportunityEntity> findFirstByOpportunityIdOrderByVersionDesc(UUID opportunityId);

    @Query("""
            select opportunity from JpaTradingOpportunityEntity opportunity
            where opportunity.strategyMatchId in :strategyMatchIds
              and opportunity.version = (
                  select max(candidate.version) from JpaTradingOpportunityEntity candidate
                  where candidate.opportunityId = opportunity.opportunityId
              )
            """)
    List<JpaTradingOpportunityEntity> findLatestByStrategyMatchIds(
            @Param("strategyMatchIds") Collection<UUID> strategyMatchIds);
}
