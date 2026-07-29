package com.hope.trading.market_intelligence.application.strategy;

import com.hope.trading.market_intelligence.application.capability.DisabledAiAnalysisCapability;
import com.hope.trading.market_intelligence.application.capability.SpreadAnalysisCapability;
import com.hope.trading.market_intelligence.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class PassiveAnalysisStrategy implements AnalysisExecutionStrategy {
    private final Duration timeout;

    public PassiveAnalysisStrategy(
            @Value("${intelligence.passive.timeout:750ms}") Duration timeout
    ) {
        this.timeout = timeout;
    }

    @Override
    public AnalysisExecutionMode mode() {
        return AnalysisExecutionMode.PASSIVE;
    }

    @Override
    public AnalysisExecutionPlan plan(IntelligenceAnalysisRequest request) {
        return new AnalysisExecutionPlan(
                List.of(
                        SpreadAnalysisCapability.CAPABILITY_ID,
                        DisabledAiAnalysisCapability.CAPABILITY_ID
                ),
                List.of(
                        ContextRequirement.requiredPublic(ContextSectionType.MARKET_IDENTITY),
                        ContextRequirement.requiredPublic(ContextSectionType.MARKET_SNAPSHOT)
                ),
                2,
                timeout
        );
    }
}
