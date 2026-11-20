package com.hope.trading.market_intelligence.application.ai;

import com.hope.trading.market_intelligence.domain.ai.AiInterpretationMode;
import com.hope.trading.market_intelligence.domain.ai.ContextPack;
import com.hope.trading.market_intelligence.domain.ai.AiAnalysisEligibility;

public record OpportunityInterpretationDecision(
        AiInterpretationMode mode,
        ContextPack contextPack,
        AiAnalysisEligibility eligibility
) {
}
