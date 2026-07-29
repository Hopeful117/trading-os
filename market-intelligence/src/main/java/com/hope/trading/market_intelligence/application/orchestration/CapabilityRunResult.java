package com.hope.trading.market_intelligence.application.orchestration;

import com.hope.trading.market_intelligence.application.capability.CapabilityAnalysisResult;
import com.hope.trading.market_intelligence.domain.CapabilityExecution;

record CapabilityRunResult(
        CapabilityExecution execution,
        CapabilityAnalysisResult result
) {
}
