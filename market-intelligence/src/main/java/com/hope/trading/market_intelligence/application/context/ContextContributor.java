package com.hope.trading.market_intelligence.application.context;

import com.hope.trading.market_intelligence.domain.ContextSection;
import com.hope.trading.market_intelligence.domain.ContextSectionType;
import com.hope.trading.market_intelligence.domain.IntelligenceAnalysisRequest;

public interface ContextContributor {
    ContextSectionType sectionType();

    ContextSection contribute(IntelligenceAnalysisRequest request);
}
