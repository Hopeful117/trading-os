package com.hope.trading.market_intelligence.application.passive;

import com.hope.trading.market_intelligence.adapter.marketdata.MarketResponse;
import com.hope.trading.market_intelligence.domain.opportunity.TradingOpportunity;
import com.hope.trading.market_intelligence.domain.observation.Observation;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesis;

import java.util.List;

public record PassiveAwarenessProjection(
        TradingOpportunity opportunity,
        MarketResponse market,
        MarketThesis thesis,
        List<Observation> observations,
        List<String> diagnostics,
        boolean current
) {
    public PassiveAwarenessProjection {
        diagnostics = List.copyOf(diagnostics);
        observations = List.copyOf(observations);
    }
}
