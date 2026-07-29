package com.hope.trading.market_intelligence.application.observation;

import com.hope.trading.market_intelligence.domain.capability.CapabilityExecution;

import java.util.List;

public interface ObservationConsolidationRule {
    String version();
    ObservationRuleResult evaluate(String instrument, List<CapabilityExecution> results);
}
