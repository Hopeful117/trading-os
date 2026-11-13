package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.domain.research.ResearchHypothesis;

public interface ResearchHypothesisRepository {
    ResearchHypothesis save(ResearchHypothesis hypothesis);
}
