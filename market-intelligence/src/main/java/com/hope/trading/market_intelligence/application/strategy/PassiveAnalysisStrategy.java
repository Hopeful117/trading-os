package com.hope.trading.market_intelligence.application.strategy;

import com.hope.trading.market_intelligence.application.capability.OhlcRangeAnalysisCapability;
import com.hope.trading.market_intelligence.application.capability.SpreadAnalysisCapability;
import com.hope.trading.market_intelligence.application.capability.TrendContextAnalysisCapability;
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
        boolean strictObserved = request.evidencePolicy() == EvidenceAcquisitionPolicy.STRICT_OBSERVED;
        List<ContextRequirement> baselineContext = strictObserved
                ? List.of(
                        ContextRequirement.optionalPublic(ContextSectionType.MARKET_IDENTITY),
                        ContextRequirement.requiredPublic(ContextSectionType.HISTORICAL_OHLC),
                        ContextRequirement.requiredPublic(ContextSectionType.TREND_CONTEXT)
                )
                : List.of(
                        ContextRequirement.requiredPublic(ContextSectionType.MARKET_IDENTITY),
                        ContextRequirement.requiredPublic(ContextSectionType.MARKET_SNAPSHOT)
                );
        List<String> capabilities = strictObserved
                ? List.of(
                        TrendContextAnalysisCapability.CAPABILITY_ID,
                        OhlcRangeAnalysisCapability.CAPABILITY_ID)
                : List.of(
                        TrendContextAnalysisCapability.CAPABILITY_ID,
                        SpreadAnalysisCapability.CAPABILITY_ID
                );
        return new AnalysisExecutionPlan(
                capabilities,
                baselineContext,
                capabilities.size(),
                timeout
        );
    }
}
