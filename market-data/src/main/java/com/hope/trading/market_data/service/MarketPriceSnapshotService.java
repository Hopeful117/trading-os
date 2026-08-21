package com.hope.trading.market_data.service;

import com.hope.trading.market_data.dto.MarketPriceSnapshot;
import com.hope.trading.market_data.dto.MarketPriceSnapshotStatus;
import com.hope.trading.market_data.brokerClient.MarketDataProvider;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.TickerEvent;
import com.hope.trading.market_data.repository.MarketRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.Clock;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class MarketPriceSnapshotService {
    private final MarketRepository marketRepository;
    private final TickerEventPublisher tickerEventPublisher;
    private final Map<MarketProvider, MarketDataProvider> providers;
    private final Clock clock;
    private final Duration staleAfter;
    private final ConcurrentMap<UUID, CompletableFuture<Optional<TickerEvent>>> inFlightAcquisitions =
            new ConcurrentHashMap<>();

    public MarketPriceSnapshotService(
            MarketRepository marketRepository,
            TickerEventPublisher tickerEventPublisher,
            List<MarketDataProvider> providers,
            Clock clock,
            @Value("${market-data.snapshot.stale-after:30s}") Duration staleAfter) {
        this.marketRepository = marketRepository;
        this.tickerEventPublisher = tickerEventPublisher;
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
                MarketDataProvider::getName,
                Function.identity()
        ));
        this.clock = clock;
        this.staleAfter = staleAfter;
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

        if (ticker != null && isFresh(ticker)) {
            return toSnapshot(marketId, market, tradable, ticker, MarketPriceSnapshotStatus.FRESH);
        }

        Optional<TickerEvent> refreshed = acquireCurrentState(market);
        if (refreshed.isPresent() && isFresh(refreshed.get())) {
            return toSnapshot(
                    marketId,
                    market,
                    tradable,
                    refreshed.get(),
                    MarketPriceSnapshotStatus.FRESH
            );
        }

        if (ticker != null && hasUsablePrice(ticker)) {
            return toSnapshot(marketId, market, tradable, ticker, MarketPriceSnapshotStatus.STALE);
        }

        if (refreshed.isPresent() && hasUsablePrice(refreshed.get())) {
            return toSnapshot(
                    marketId,
                    market,
                    tradable,
                    refreshed.get(),
                    MarketPriceSnapshotStatus.STALE
            );
        }

        if (ticker == null || !hasUsablePrice(ticker)) {
            return new MarketPriceSnapshot(
                    marketId, market.getSymbol(), null, null, null, tradable, null,
                    MarketPriceSnapshotStatus.UNAVAILABLE, null, null, clock.instant()
            );
        }
        return toSnapshot(marketId, market, tradable, ticker, MarketPriceSnapshotStatus.STALE);
    }

    private MarketPriceSnapshot toSnapshot(
            UUID marketId,
            Market market,
            boolean tradable,
            TickerEvent ticker,
            MarketPriceSnapshotStatus status
    ) {
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
                status,
                "ticker:" + sourceIdentity,
                sourceVersion == 0 ? 1 : sourceVersion,
                ticker.occurredAt() == null ? clock.instant() : ticker.occurredAt()
        );
    }

    private Optional<TickerEvent> acquireCurrentState(Market market) {
        MarketDataProvider provider = providers.get(market.getProvider());
        if (provider == null) {
            log.warn("No MarketDataProvider registered for provider={}", market.getProvider());
            return Optional.empty();
        }
        while (true) {
            TickerEvent cached = tickerEventPublisher.latestByMarketId(market.getMarketId()).orElse(null);
            if (cached != null && isFresh(cached)) {
                return Optional.of(cached);
            }

            CompletableFuture<Optional<TickerEvent>> claim = new CompletableFuture<>();
            CompletableFuture<Optional<TickerEvent>> existing =
                    inFlightAcquisitions.putIfAbsent(market.getMarketId(), claim);
            if (existing == null) {
                try {
                    Optional<TickerEvent> acquired = provider.acquireCurrentSnapshot(market);
                    acquired.ifPresent(tickerEventPublisher::recordCurrentState);
                    claim.complete(acquired);
                    return acquired;
                } catch (RuntimeException exception) {
                    log.warn(
                            "Current snapshot acquisition failed marketId={} provider={} message={}",
                            market.getMarketId(),
                            market.getProvider(),
                            exception.getMessage()
                    );
                    claim.completeExceptionally(exception);
                    return Optional.empty();
                } finally {
                    inFlightAcquisitions.remove(market.getMarketId(), claim);
                }
            }
            try {
                return existing.join();
            } catch (CompletionException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException runtime) {
                    log.warn(
                            "Waiting snapshot acquisition failed marketId={} provider={} message={}",
                            market.getMarketId(),
                            market.getProvider(),
                            runtime.getMessage()
                    );
                    return Optional.empty();
                }
                throw exception;
            }
        }
    }

    private boolean isFresh(TickerEvent ticker) {
        return ticker != null
                && ticker.occurredAt() != null
                && !ticker.occurredAt().isBefore(clock.instant().minus(staleAfter))
                && hasUsablePrice(ticker);
    }

    private boolean hasUsablePrice(TickerEvent ticker) {
        return ticker != null && ticker.bid() != null && ticker.ask() != null;
    }
}
