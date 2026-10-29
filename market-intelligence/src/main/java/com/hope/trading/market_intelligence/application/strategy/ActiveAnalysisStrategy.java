package com.hope.trading.market_intelligence.application.strategy;

import com.hope.trading.market_intelligence.application.capability.DisabledAiAnalysisCapability;
import com.hope.trading.market_intelligence.application.capability.SpreadAnalysisCapability;
import com.hope.trading.market_intelligence.application.capability.OhlcRangeAnalysisCapability;
import com.hope.trading.market_intelligence.application.capability.TrendContextAnalysisCapability;
import com.hope.trading.market_intelligence.application.capability.OhlcMomentumAnalysisCapability;
import com.hope.trading.market_intelligence.application.capability.TradeabilityAnalysisCapability;
import com.hope.trading.market_intelligence.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class ActiveAnalysisStrategy implements AnalysisExecutionStrategy {
    private final Duration timeout;

    public ActiveAnalysisStrategy(
            @Value("${intelligence.active.timeout:3s}") Duration timeout
    ) {
        this.timeout = timeout;
    }

    @Override
    public AnalysisExecutionMode mode() {
        return AnalysisExecutionMode.ACTIVE;
    }

    @Override
    public AnalysisExecutionPlan plan(IntelligenceAnalysisRequest request) {
        if (request.evidencePolicy() == EvidenceAcquisitionPolicy.STRICT_OBSERVED) {
            return new AnalysisExecutionPlan(
                    List.of(
                             TrendContextAnalysisCapability.CAPABILITY_ID,
                             OhlcRangeAnalysisCapability.CAPABILITY_ID,
                             OhlcMomentumAnalysisCapability.CAPABILITY_ID,
                             TradeabilityAnalysisCapability.CAPABILITY_ID
                    ),
                    List.of(
                            ContextRequirement.optionalPublic(ContextSectionType.MARKET_IDENTITY),
                            ContextRequirement.requiredPublic(ContextSectionType.MARKET_SNAPSHOT),
                            ContextRequirement.requiredPublic(ContextSectionType.HISTORICAL_OHLC),
                            ContextRequirement.optionalPublic(ContextSectionType.TREND_CONTEXT)
                    ),
                      4,
                    timeout
            );
        }
        return new AnalysisExecutionPlan(
                List.of(
                         TrendContextAnalysisCapability.CAPABILITY_ID,
                         SpreadAnalysisCapability.CAPABILITY_ID,
                         OhlcRangeAnalysisCapability.CAPABILITY_ID,
                          OhlcMomentumAnalysisCapability.CAPABILITY_ID,
                          DisabledAiAnalysisCapability.CAPABILITY_ID
                ),
                List.of(
                        ContextRequirement.requiredPublic(ContextSectionType.MARKET_IDENTITY),
                        ContextRequirement.requiredPublic(ContextSectionType.MARKET_SNAPSHOT),
                        ContextRequirement.optionalPublic(ContextSectionType.HISTORICAL_OHLC),
                             ContextRequirement.optionalPublic(ContextSectionType.ORDER_FLOW),
                             ContextRequirement.optionalPublic(ContextSectionType.ORDER_BOOK),
                        ContextRequirement.optionalPublic(ContextSectionType.NEWS)
                ),
                8,
                timeout
        );
    }
}
