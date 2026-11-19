package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.domain.opportunity.OpportunityId;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityVersion;
import com.hope.trading.market_intelligence.domain.opportunityoutcome.OpportunityOutcome;
import com.hope.trading.market_intelligence.domain.opportunityoutcome.OpportunityOutcomeIdentity;
import com.hope.trading.market_intelligence.domain.opportunityoutcome.OpportunityOutcomeStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OpportunityOutcomeRepository {
    Optional<OpportunityOutcome> findByFingerprint(String fingerprint);

    Optional<OpportunityOutcome> findByIdentity(OpportunityOutcomeIdentity identity);

    List<OpportunityOutcome> findByOpportunity(OpportunityId opportunityId,
                                               OpportunityVersion opportunityVersion);

    List<OpportunityOutcome> findByStatusAndComputedBefore(OpportunityOutcomeStatus status,
                                                            Instant computedBefore,
                                                            int limit);

    OpportunityOutcome saveIfAbsent(OpportunityOutcome outcome);
}
