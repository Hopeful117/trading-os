package com.hope.trading.market_intelligence.adapter.marketdata;

import com.hope.trading.market_intelligence.application.context.ContextContributor;
import com.hope.trading.market_intelligence.domain.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MarketSnapshotContextContributor implements ContextContributor {
    private final MarketDataClient marketDataClient;
    private final MarketDataSectionFactory sectionFactory;

    public MarketSnapshotContextContributor(
            MarketDataClient marketDataClient,
            MarketDataSectionFactory sectionFactory
    ) {
        this.marketDataClient = marketDataClient;
        this.sectionFactory = sectionFactory;
    }

    @Override
    public ContextSectionType sectionType() {
        return ContextSectionType.MARKET_SNAPSHOT;
    }

    @Override
    public ContextSection contribute(IntelligenceAnalysisRequest request) {
        MarketPriceSnapshotResponse snapshot = marketDataClient.findPriceSnapshots(
                new MarketPriceSnapshotRequest(List.of(request.marketId()))
        ).stream().findFirst().orElse(null);
        ContextRequirement requirement =
                ContextRequirement.requiredPublic(sectionType());
        if (snapshot == null || !"AVAILABLE".equals(snapshot.status())) {
            return ContextSection.missing(requirement, "Current market price is unavailable");
        }
        MarketSnapshotContext payload = new MarketSnapshotContext(
                snapshot.marketId(),
                snapshot.symbol(),
                snapshot.lastPrice(),
                snapshot.bid(),
                snapshot.ask(),
                snapshot.tradable(),
                snapshot.occurredAt()
        );
        return sectionFactory.available(sectionType(), payload, snapshot.occurredAt());
    }
}
