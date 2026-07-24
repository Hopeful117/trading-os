package com.hope.trading.market_data.service;

import com.hope.trading.market_data.model.MarketDataEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@Slf4j
public class MarketDataEventPublisher {

    /**
     * Flux temps réel unique pour tous les événements marché.
     */
    private final Sinks.Many<MarketDataEvent> sink =
            Sinks.many()
                    .multicast()
                    .directBestEffort();

    /**
     * Dernière valeur connue par symbole.
     *
     * Ce cache permet à un nouveau client de recevoir immédiatement
     * le dernier prix connu avant de continuer avec le flux temps réel.
     */
    private final ConcurrentMap<String, MarketDataEvent> latestEvents =
            new ConcurrentHashMap<>();

    public void publish(MarketDataEvent event) {

        if (event == null || event.getSymbol() == null) {
            log.warn("Ignoring market data event without symbol");
            return;
        }

        String normalizedSymbol =
                normalize(event.getSymbol());

        latestEvents.put(
                normalizedSymbol,
                event
        );

        Sinks.EmitResult result =
                sink.tryEmitNext(event);

        log.info(
                "[PUBLISHER] symbol={} last={} result={}",
                event.getSymbol(),
                event.getLast(),
                result);

        if (result.isFailure()
                && result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {

            log.warn(
                    "Unable to publish market event symbol={} result={}",
                    normalizedSymbol,
                    result
            );
        }

        log.debug(
                "Market event published symbol={} last={} result={}",
                normalizedSymbol,
                event.getLast(),
                result
        );
    }

    /**
     * Flux de tous les marchés.
     */
    public Flux<MarketDataEvent> stream() {

        return sink.asFlux()
                .doOnSubscribe(ignored ->
                        log.info(
                                "Frontend subscribed to all market streams"
                        )
                )
                .doOnNext(event ->
                        log.debug(
                                "Forwarding market event symbol={} last={}",
                                event.getSymbol(),
                                event.getLast()
                        )
                );
    }

    /**
     * Flux d'un marché précis.
     *
     * Le dernier événement connu est d'abord rejoué, puis les nouvelles
     * valeurs sont transmises en temps réel.
     */
    public Flux<MarketDataEvent> streamBySymbol(String symbol) {

        String normalizedSymbol =
                normalize(symbol);

        Flux<MarketDataEvent> snapshot =
                Flux.defer(() -> {

                    MarketDataEvent latestEvent =
                            latestEvents.get(normalizedSymbol);

                    if (latestEvent == null) {
                        return Flux.empty();
                    }

                    return Flux.just(latestEvent);
                });

        Flux<MarketDataEvent> liveStream =
                sink.asFlux()
                        .filter(event ->
                                event.getSymbol() != null
                                        && normalize(event.getSymbol())
                                        .equals(normalizedSymbol)
                        );

        return Flux.concat(
                        snapshot,
                        liveStream
                )
                .doOnSubscribe(ignored ->
                        log.info(
                                "Frontend subscribed to market stream symbol={}",
                                normalizedSymbol
                        )
                )
                .doOnNext(event ->
                        log.debug(
                                "Forwarding market event symbol={} last={}",
                                normalizedSymbol,
                                event.getLast()
                        )
                );
    }

    public MarketDataEvent getLatest(String symbol) {

        if (symbol == null || symbol.isBlank()) {
            return null;
        }

        return latestEvents.get(
                normalize(symbol)
        );
    }

    public Map<String, MarketDataEvent> getLatestEventsSnapshot() {
        return Map.copyOf(latestEvents);
    }

    private String normalize(String symbol) {

        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException(
                    "Market symbol is required"
            );
        }

        return symbol
                .trim()
                .toUpperCase(Locale.ROOT);
    }
}