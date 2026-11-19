package com.hope.trading.market_intelligence.adapter.persistence;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataOpportunityOutcomeRepository
        extends JpaRepository<JpaOpportunityOutcomeEntity, UUID> {
    Optional<JpaOpportunityOutcomeEntity> findByOutcomeFingerprint(String outcomeFingerprint);

    List<JpaOpportunityOutcomeEntity> findByOpportunityIdAndOpportunityVersion(
            UUID opportunityId, long opportunityVersion);

    List<JpaOpportunityOutcomeEntity> findByStatusAndComputedAtBeforeOrderByComputedAtAsc(
            String status, Instant computedBefore, PageRequest pageRequest);
}
