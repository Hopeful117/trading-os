package com.hope.trading.market_intelligence.adapter.marketdata;

import com.hope.trading.market_intelligence.application.context.ContextContributor;
import com.hope.trading.market_intelligence.domain.*;
import org.springframework.stereotype.Component;

@Component
public class MarketIdentityContextContributor implements ContextContributor {
    private final MarketDataClient marketDataClient;
    private final MarketDataSectionFactory sectionFactory;

    public MarketIdentityContextContributor(
            MarketDataClient marketDataClient,
            MarketDataSectionFactory sectionFactory
    ) {
        this.marketDataClient = marketDataClient;
        this.sectionFactory = sectionFactory;
    }

    @Override
    public ContextSectionType sectionType() {
        return ContextSectionType.MARKET_IDENTITY;
    }

    @Override
    public ContextSection contribute(IntelligenceAnalysisRequest request) {
        MarketResponse market = marketDataClient.findMarket(request.marketId());
        boolean tradable = market.marketState() != null && market.marketState().tradable();
        MarketIdentityContext payload = new MarketIdentityContext(
                market.marketId(),
                market.provider(),
                market.symbol(),
                market.baseAsset(),
                market.quoteAsset(),
                tradable
        );
        return sectionFactory.available(
                sectionType(),
                payload,
                market.marketState() == null ? null : market.marketState().lastUpdated()
        );
    }
}
