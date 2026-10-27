package com.hope.trading.trading_core.opportunitydecision.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpportunityDecisionRepository extends JpaRepository<OpportunityDecisionEntity, UUID> {
    Optional<OpportunityDecisionEntity> findByActorIdAndIdempotencyKey(UUID actorId, String idempotencyKey);
    Optional<OpportunityDecisionEntity> findByAccountIdAndOpportunityIdAndOpportunityVersionAndDecision(
            UUID accountId, UUID opportunityId, long opportunityVersion, String decision);
}
