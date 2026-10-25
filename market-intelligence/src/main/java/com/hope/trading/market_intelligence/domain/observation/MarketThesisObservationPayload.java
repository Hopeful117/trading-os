package com.hope.trading.market_intelligence.domain.observation;

import com.hope.trading.market_intelligence.domain.momentum.MomentumCapabilityContent;
import com.hope.trading.market_intelligence.domain.trendcontext.TrendContextCapabilityContent;
import com.hope.trading.market_intelligence.domain.tradeability.TradeabilityCapabilityContent;

public record MarketThesisObservationPayload(
        TrendContextCapabilityContent trendContext,
        MomentumCapabilityContent momentum,
        TradeabilityCapabilityContent tradeability
) implements ObservationPayload {
    public MarketThesisObservationPayload(
            TrendContextCapabilityContent trendContext,
            MomentumCapabilityContent momentum) {
        this(trendContext, momentum, null);
    }
}
