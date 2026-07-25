package com.hope.trading.market_data.service;

import com.hope.trading.market_data.model.TickerEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@Slf4j
public class TickerEventPublisher {
    private final Sinks.Many<TickerEvent> sink =
            Sinks.many()
                    .multicast()
                    .directBestEffort();

    private final ConcurrentMap<String, TickerEvent> latestEvents =
            new ConcurrentHashMap<>();

    public void publish(TickerEvent event) {
        if (event == null || event.symbol() == null) {
            log.warn("Ignoring ticker event without symbol");
            return;
        }

        String symbol = normalize(event.symbol());

        latestEvents.put(symbol, event);

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

    private String normalize(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}
