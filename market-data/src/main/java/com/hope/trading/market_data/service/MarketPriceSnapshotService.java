package com.hope.trading.market_data.service;

import com.hope.trading.market_data.dto.MarketPriceSnapshot;
import com.hope.trading.market_data.dto.MarketPriceSnapshotStatus;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.TickerEvent;
import com.hope.trading.market_data.repository.MarketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.Clock;
import java.nio.charset.StandardCharsets;

@Service
public class MarketPriceSnapshotService {
    private final MarketRepository marketRepository;
    private final TickerEventPublisher tickerEventPublisher;
    private final Clock clock;

    public MarketPriceSnapshotService(
            MarketRepository marketRepository, TickerEventPublisher tickerEventPublisher,
            Clock clock) {
        this.marketRepository = marketRepository;
        this.tickerEventPublisher = tickerEventPublisher;
        this.clock = clock;
    }

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
                    MarketPriceSnapshotStatus.UNKNOWN_MARKET, null, null, clock.instant()
            );
        }

        TickerEvent ticker = tickerEventPublisher.latestByMarketId(marketId).orElse(null);
        boolean tradable = market.getMarketState() != null
                && market.getMarketState().isTradable();

        if (ticker == null || ticker.last() == null) {
            return new MarketPriceSnapshot(
                    marketId, market.getSymbol(), null, null, null, tradable, null,
                    MarketPriceSnapshotStatus.PRICE_UNAVAILABLE, null, null, clock.instant()
            );
        }

        UUID sourceIdentity = UUID.nameUUIDFromBytes((
                marketId + "|" + ticker.provider() + "|" + ticker.symbol() + "|"
                        + ticker.bid() + "|" + ticker.ask() + "|" + ticker.last() + "|"
                        + ticker.volume() + "|" + ticker.occurredAt())
                .getBytes(StandardCharsets.UTF_8));
        long sourceVersion = sourceIdentity.getMostSignificantBits() & Long.MAX_VALUE;
        return new MarketPriceSnapshot(
                marketId,
                market.getSymbol(),
                ticker.last(),
                ticker.bid(),
                ticker.ask(),
                tradable,
                ticker.occurredAt(),
                MarketPriceSnapshotStatus.AVAILABLE,
                "ticker:" + sourceIdentity,
                sourceVersion == 0 ? 1 : sourceVersion,
                clock.instant()
        );
    }
}
