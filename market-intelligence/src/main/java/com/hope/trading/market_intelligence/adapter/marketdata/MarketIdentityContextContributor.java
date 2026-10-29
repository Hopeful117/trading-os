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
        if (!request.marketId().equals(market.marketId())) {
            throw new IllegalStateException("Market identity response does not match the requested market");
        }
        boolean tradable = market.marketState() != null && market.marketState().tradable();
        MarketIdentityContext payload = new MarketIdentityContext(
                market.marketId(),
                market.provider(),
                market.symbol(),
                market.baseAsset(),
                market.quoteAsset(),
                tradable
        );
        ContextSection section = sectionFactory.availableAt(
                sectionType(),
                payload,
                market.marketState() == null ? null : market.marketState().lastUpdated(),
                market.marketState() == null ? null : market.marketState().lastUpdated(),
                request.evaluationCutoff()
        );
        ContextProvenance provenance = section.provenance();
        if (provenance == null) {
            return section;
        }
        return new ContextSection(
                section.type(), section.status(), section.sensitivity(), section.payload(),
                new ContextProvenance(
                        provenance.source(), provenance.sourceOccurredAt(), provenance.fetchedAt(),
                        provenance.sourceReference(), provenance.sourceVersion(), provenance.normalizationVersion(),
                        request.evidencePolicy(), provenance.evidenceFingerprint(), provenance.observationCount(),
                        request.evaluationCutoff()),
                section.message());
    }
}
