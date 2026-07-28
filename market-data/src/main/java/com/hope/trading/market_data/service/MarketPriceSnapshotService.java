package com.hope.trading.market_data.service;

import com.hope.trading.market_data.dto.MarketPriceSnapshot;
import com.hope.trading.market_data.dto.MarketPriceSnapshotStatus;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.TickerEvent;
import com.hope.trading.market_data.repository.MarketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MarketPriceSnapshotService {
    private final MarketRepository marketRepository;
    private final TickerEventPublisher tickerEventPublisher;

    @Transactional(readOnly = true)
    public List<MarketPriceSnapshot> findSnapshots(List<UUID> marketIds) {
        List<UUID> requestedIds = marketIds.stream().distinct().toList();
        Map<UUID, Market> markets = marketRepository.findAllById(requestedIds)
                .stream()
                .collect(Collectors.toMap(Market::getMarketId, Function.identity()));

        return requestedIds.stream()
                .map(marketId -> toSnapshot(marketId, markets.get(marketId)))
                .toList();
    }

    private MarketPriceSnapshot toSnapshot(UUID marketId, Market market) {
        if (market == null) {
            return new MarketPriceSnapshot(
                    marketId, null, null, null, null, false, null,
                    MarketPriceSnapshotStatus.UNKNOWN_MARKET
            );
        }

        TickerEvent ticker = tickerEventPublisher.latestByMarketId(marketId).orElse(null);
        boolean tradable = market.getMarketState() != null
                && market.getMarketState().isTradable();

        if (ticker == null || ticker.last() == null) {
            return new MarketPriceSnapshot(
                    marketId, market.getSymbol(), null, null, null, tradable, null,
                    MarketPriceSnapshotStatus.PRICE_UNAVAILABLE
            );
        }

        return new MarketPriceSnapshot(
                marketId,
                market.getSymbol(),
                ticker.last(),
                ticker.bid(),
                ticker.ask(),
                tradable,
                ticker.occurredAt(),
                MarketPriceSnapshotStatus.AVAILABLE
        );
    }
}
