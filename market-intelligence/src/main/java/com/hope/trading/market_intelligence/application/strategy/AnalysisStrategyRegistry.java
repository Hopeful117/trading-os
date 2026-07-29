package com.hope.trading.market_intelligence.application.strategy;

import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class AnalysisStrategyRegistry {
    private final Map<AnalysisExecutionMode, AnalysisExecutionStrategy> strategies;

    public AnalysisStrategyRegistry(List<AnalysisExecutionStrategy> strategies) {
        Map<AnalysisExecutionMode, AnalysisExecutionStrategy> indexed =
                new EnumMap<>(AnalysisExecutionMode.class);
        strategies.forEach(strategy -> indexed.put(strategy.mode(), strategy));
        this.strategies = Map.copyOf(indexed);
    }

    public AnalysisExecutionStrategy strategy(AnalysisExecutionMode mode) {
        AnalysisExecutionStrategy strategy = strategies.get(mode);
        if (strategy == null) {
            throw new IllegalArgumentException("No strategy registered for " + mode);
        }
        return strategy;
    }
}
