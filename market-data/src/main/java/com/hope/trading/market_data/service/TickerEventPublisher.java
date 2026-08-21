package com.hope.trading.market_data.service;

import com.hope.trading.market_data.model.TickerEvent;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.PriceObservation;
import com.hope.trading.market_data.repository.MarketRepository;
import com.hope.trading.market_data.repository.PriceObservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@Slf4j
@RequiredArgsConstructor
public class TickerEventPublisher {
    private final MarketRepository marketRepository;
    private final PriceObservationRepository priceObservationRepository;
    private final Clock clock;
    private final Sinks.Many<TickerEvent> sink =
            Sinks.many()
                    .multicast()
                    .directBestEffort();

    private final ConcurrentMap<String, TickerEvent> latestEvents =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, TickerEvent> latestEventsByMarketId =
            new ConcurrentHashMap<>();

    public void publish(TickerEvent event) {
        String symbol = store(event);
        if (symbol == null) {
            return;
        }

        Sinks.EmitResult result = sink.tryEmitNext(event);

        if (result.isFailure()
                && result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
            log.warn(
                    "Unable to publish ticker event symbol={} result={}",
                    symbol,
                    result
            );
        }
    }

    public void recordCurrentState(TickerEvent event) {
        store(event);
    }

    private void persistObservation(TickerEvent event) {
        if (event.marketId() == null || event.provider() == null || event.occurredAt() == null) {
            log.warn("Ignoring persistence for incomplete ticker event symbol={}", event.symbol());
            return;
        }
        Market market = marketRepository.findById(event.marketId()).orElse(null);
        if (market == null) {
            log.warn("Ignoring ticker observation for unknown marketId={}", event.marketId());
            return;
        }
        priceObservationRepository.save(new PriceObservation(
                UUID.randomUUID(), event.marketId(), event.provider(), event.symbol(),
                market.getBaseAsset(), market.getQuoteAsset(), event.bid(), event.ask(), event.last(),
                event.occurredAt(), Instant.now(clock)
        ));
    }

    public Flux<TickerEvent> streamBySymbol(String symbol) {
        String normalizedSymbol = normalize(symbol);

        Flux<TickerEvent> snapshot = Flux.defer(() -> {
            TickerEvent latest = latestEvents.get(normalizedSymbol);
            return latest == null
                    ? Flux.empty()
                    : Flux.just(latest);
        });

        Flux<TickerEvent> liveStream = sink.asFlux()
                .filter(event ->
                        normalize(event.symbol())
                                .equals(normalizedSymbol)
                );

        return Flux.concat(snapshot, liveStream);
    }

    public Optional<TickerEvent> latestByMarketId(UUID marketId) {
        return Optional.ofNullable(latestEventsByMarketId.get(marketId));
    }

    private String store(TickerEvent event) {
        if (event == null || event.symbol() == null) {
            log.warn("Ignoring ticker event without symbol");
            return null;
        }

        String symbol = normalize(event.symbol());

        persistObservation(event);

        latestEvents.put(symbol, event);
        if (event.marketId() != null) {
            latestEventsByMarketId.put(event.marketId(), event);
        }
        return symbol;
    }

    private String normalize(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}
