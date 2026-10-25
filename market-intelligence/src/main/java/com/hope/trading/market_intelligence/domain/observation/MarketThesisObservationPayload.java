package com.hope.trading.market_intelligence.domain.observation;

import com.hope.trading.market_intelligence.domain.momentum.MomentumCapabilityContent;
import com.hope.trading.market_intelligence.domain.trendcontext.TrendContextCapabilityContent;

public record MarketThesisObservationPayload(
        TrendContextCapabilityContent trendContext,
        MomentumCapabilityContent momentum
) implements ObservationPayload {
}
