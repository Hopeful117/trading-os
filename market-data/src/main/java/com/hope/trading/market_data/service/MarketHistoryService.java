package com.hope.trading.market_data.service;

import com.hope.trading.market_data.brokerClient.MarketDataProvider;
import com.hope.trading.market_data.exception.EntityNotFoundException;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.OhlcEvent;
import com.hope.trading.market_data.model.OhlcInterval;
import com.hope.trading.market_data.repository.MarketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MarketHistoryService {

    private static final int MAX_OHLC_LIMIT = 720;

    private final MarketRepository marketRepository;
    private final MarketDataProvider marketDataProvider;

    public List<OhlcEvent> findOhlcHistory(
            UUID marketId,
            OhlcInterval interval,
            int limit
    ) {
        validateRequest(interval, limit);

        Market market = marketRepository.findById(marketId)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Market not found: " + marketId
                        )
                );

        return marketDataProvider.findOhlcHistory(
                market,
                interval,
                limit
        );
    }

    private void validateRequest(
            OhlcInterval interval,
            int limit
    ) {
        if (interval == null) {
            throw new IllegalArgumentException(
                    "OHLC interval is required"
            );
        }

        if (limit < 1 || limit > MAX_OHLC_LIMIT) {
            throw new IllegalArgumentException(
                    "OHLC history limit must be between 1 and "
                            + MAX_OHLC_LIMIT
            );
        }
    }
}