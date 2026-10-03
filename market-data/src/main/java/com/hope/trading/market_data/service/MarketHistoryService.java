package com.hope.trading.market_data.service;

import com.hope.trading.market_data.brokerClient.MarketDataProvider;
import com.hope.trading.market_data.exception.EntityNotFoundException;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.MarketHistorySnapshot;
import com.hope.trading.market_data.model.OhlcEvent;
import com.hope.trading.market_data.model.OhlcInterval;
import com.hope.trading.market_data.repository.MarketRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MarketHistoryService {

    private static final int MAX_OHLC_LIMIT = 720;

    private final MarketRepository marketRepository;
    private final Map<MarketProvider, MarketDataProvider> providers;

    public MarketHistoryService(
            MarketRepository marketRepository,
            List<MarketDataProvider> providers
    ) {
        this.marketRepository = marketRepository;
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
                MarketDataProvider::getName,
                Function.identity()
        ));
    }

    public List<OhlcEvent> findOhlcHistory(
            UUID marketId,
            OhlcInterval interval,
            int limit
    ) {
        return findOhlcHistorySnapshot(marketId, interval, limit).normalizedEvents();
    }

    public MarketHistorySnapshot findOhlcHistorySnapshot(
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

        MarketDataProvider provider = providers.get(market.getProvider());
        if (provider == null) {
            throw new UnsupportedOperationException(
                    "No market data provider registered for " + market.getProvider());
        }

        return provider.findOhlcHistorySnapshot(
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
