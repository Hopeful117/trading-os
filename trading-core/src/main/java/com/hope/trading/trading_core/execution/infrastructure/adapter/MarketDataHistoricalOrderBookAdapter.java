package com.hope.trading.trading_core.execution.infrastructure.adapter;

import com.hope.trading.trading_core.execution.application.port.HistoricalOrderBookPort;
import com.hope.trading.trading_core.market_data.apiClient.HistoricalOrderBookClient;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public final class MarketDataHistoricalOrderBookAdapter implements HistoricalOrderBookPort {
    private final HistoricalOrderBookClient client;

    public MarketDataHistoricalOrderBookAdapter(HistoricalOrderBookClient client) {
        this.client = client;
    }

    @Override
    public Replay replay(UUID marketId, int depth, Instant executionCutoff) {
        HistoricalOrderBookClient.ReplayResponse response = client.replay(marketId, depth, executionCutoff, null);
        Status status;
        try {
            status = Status.valueOf(response.status());
        } catch (RuntimeException exception) {
            status = Status.UNAVAILABLE;
        }
        return new Replay(status, response.marketId(), response.depth(), response.sequenceId(),
                response.evaluationCutoff(), response.latestOccurredAt(), response.latestKnownAt(),
                response.sourceStartOrdinal(), response.sourceEndOrdinal(), response.provider(),
                response.providerSymbol(), response.normalizationVersion(), response.sourceEvidenceFingerprint(),
                response.bids().stream().map(value -> new Level(value.price(), value.quantity())).toList(),
                response.asks().stream().map(value -> new Level(value.price(), value.quantity())).toList(),
                response.reason());
    }
}
