package com.hope.trading.market_data.service;

import com.hope.trading.market_data.config.HistoricalOhlcCaptureProperties;
import com.hope.trading.market_data.model.OhlcInterval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class HistoricalOhlcCaptureService {
    private static final Logger log = LoggerFactory.getLogger(HistoricalOhlcCaptureService.class);
    private final MarketHistoryService marketHistoryService;
    private final HistoricalOhlcEvidenceWriter evidenceWriter;
    private final HistoricalOhlcCaptureProperties properties;

    public HistoricalOhlcCaptureService(
            MarketHistoryService marketHistoryService,
            HistoricalOhlcEvidenceWriter evidenceWriter,
            HistoricalOhlcCaptureProperties properties
    ) {
        this.marketHistoryService = marketHistoryService;
        this.evidenceWriter = evidenceWriter;
        this.properties = properties;
    }

    public void capture() {
        if (!properties.isEnabled() || properties.getMarketIds().isEmpty()) {
            return;
        }
        if (properties.getLimit() < 1 || properties.getLimit() > 720) {
            throw new IllegalArgumentException("Historical OHLC capture limit must be between 1 and 720");
        }
        for (UUID marketId : properties.getMarketIds()) {
            for (OhlcInterval interval : properties.getIntervals()) {
                try {
                    int accepted = evidenceWriter.appendCaptured(
                            marketHistoryService.findOhlcHistorySnapshot(marketId, interval, properties.getLimit())
                                    .observedEvents()).size();
                    log.info("Historical OHLC capture completed market={} interval={} accepted={}",
                            marketId, interval, accepted);
                } catch (RuntimeException exception) {
                    log.warn("Historical OHLC capture failed market={} interval={} errorType={}",
                            marketId, interval, exception.getClass().getSimpleName());
                }
            }
        }
    }
}
