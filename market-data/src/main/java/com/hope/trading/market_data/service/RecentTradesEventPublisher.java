package com.hope.trading.market_data.service;

import com.hope.trading.market_data.model.RecentTradesSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@Slf4j
public class RecentTradesEventPublisher {
    private final Sinks.Many<RecentTradesSnapshot> sink =
            Sinks.many()
                    .multicast()
                    .directBestEffort();
    private final ConcurrentMap<UUID, RecentTradesSnapshot> latestSnapshots =
            new ConcurrentHashMap<>();

    public void publish(RecentTradesSnapshot snapshot) {
        latestSnapshots.put(snapshot.marketId(), snapshot);

        Sinks.EmitResult result = sink.tryEmitNext(snapshot);

        if (result.isFailure()
                && result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
            log.warn(
                    "Unable to publish recent-trades snapshot marketId={} result={}",
                    snapshot.marketId(),
                    result
            );
        }
    }

    public Flux<RecentTradesSnapshot> streamByMarket(UUID marketId) {
        Flux<RecentTradesSnapshot> current = Flux.defer(() -> {
            RecentTradesSnapshot snapshot =
                    latestSnapshots.get(marketId);
            return snapshot == null
                    ? Flux.empty()
                    : Flux.just(snapshot);
        });
        Flux<RecentTradesSnapshot> updates = sink.asFlux()
                .filter(snapshot ->
                        snapshot.marketId().equals(marketId)
                );

        return Flux.concat(current, updates);
    }

    public void clear(UUID marketId) {
        latestSnapshots.remove(marketId);
    }
}
