package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.domain.opportunity.*;

import java.util.*;
import java.util.UUID;

public interface UserOpportunityRepository {
    UserOpportunity save(UserOpportunity projection);
    Optional<UserOpportunity> find(UUID userId, OpportunityId opportunityId);
    List<UserOpportunity> findByUser(UUID userId);
}
