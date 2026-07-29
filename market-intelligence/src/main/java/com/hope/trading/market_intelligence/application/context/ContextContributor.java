package com.hope.trading.market_intelligence.application.context;

import com.hope.trading.market_intelligence.domain.ContextSection;
import com.hope.trading.market_intelligence.domain.ContextSectionType;
import com.hope.trading.market_intelligence.domain.IntelligenceAnalysisRequest;
import com.hope.trading.market_intelligence.domain.context.ContextContributionDescriptor;

/**
 * Declares context ownership and disclosure metadata before loading any data.
 * A consumer request is not an authorization; orchestration policy remains
 * responsible for deciding what is exposed.
 */
public interface ContextContributor {
    ContextSectionType sectionType();

    default ContextContributionDescriptor descriptor() {
        return ContextContributionDescriptor.publicMarket(sectionType());
    }

    ContextSection contribute(IntelligenceAnalysisRequest request);
}
