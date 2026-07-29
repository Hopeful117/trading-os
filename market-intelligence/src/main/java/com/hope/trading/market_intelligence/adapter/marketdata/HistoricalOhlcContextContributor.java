package com.hope.trading.market_intelligence.adapter.marketdata;

import com.hope.trading.market_intelligence.application.context.ContextContributor;
import com.hope.trading.market_intelligence.domain.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Component
public class HistoricalOhlcContextContributor implements ContextContributor {
    private static final String ACTIVE_INTERVAL = "FIFTEEN_MINUTES";
    private static final int ACTIVE_LIMIT = 200;
    private final MarketDataClient marketDataClient;
    private final MarketDataSectionFactory sectionFactory;

    public HistoricalOhlcContextContributor(
            MarketDataClient marketDataClient,
            MarketDataSectionFactory sectionFactory
    ) {
        this.marketDataClient = marketDataClient;
        this.sectionFactory = sectionFactory;
    }

    @Override
    public ContextSectionType sectionType() {
        return ContextSectionType.HISTORICAL_OHLC;
    }

    @Override
    public ContextSection contribute(IntelligenceAnalysisRequest request) {
        List<OhlcResponse> response = marketDataClient.findOhlc(
                request.marketId(), ACTIVE_INTERVAL, ACTIVE_LIMIT
        );
        if (response.isEmpty()) {
            return ContextSection.missing(
                    ContextRequirement.optionalPublic(sectionType()),
                    "OHLC history is unavailable"
            );
        }
        List<OhlcPoint> candles = response.stream()
                .map(candle -> new OhlcPoint(
                        candle.openTime(),
                        candle.closeTime(),
                        candle.open(),
                        candle.high(),
                        candle.low(),
                        candle.close(),
                        candle.volume()
                ))
                .toList();
        Instant occurredAt = response.stream()
                .map(OhlcResponse::occurredAt)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return sectionFactory.available(
                sectionType(),
                new HistoricalOhlcContext(
                        request.marketId(), ACTIVE_INTERVAL, candles
                ),
                occurredAt
        );
    }
}
