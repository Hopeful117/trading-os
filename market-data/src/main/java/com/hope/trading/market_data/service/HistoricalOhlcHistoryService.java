package com.hope.trading.market_data.service;

import com.hope.trading.market_data.adapter.persistence.JpaHistoricalOhlcEvidenceRepository;
import com.hope.trading.market_data.model.HistoricalOhlcEvidencePolicy;
import com.hope.trading.market_data.model.HistoricalOhlcSeries;
import com.hope.trading.market_data.model.OhlcInterval;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class HistoricalOhlcHistoryService {
    private final JpaHistoricalOhlcEvidenceRepository repository;

    public HistoricalOhlcHistoryService(JpaHistoricalOhlcEvidenceRepository repository) {
        this.repository = repository;
    }

    public HistoricalOhlcSeries findStrictObserved(
            UUID marketId, OhlcInterval interval, Instant asOf, int limit) {
        return repository.findAsOf(
                marketId, interval, asOf, limit, HistoricalOhlcEvidencePolicy.STRICT_OBSERVED);
    }
}
