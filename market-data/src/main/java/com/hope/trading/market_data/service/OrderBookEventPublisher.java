package com.hope.trading.market_data.service;

import com.hope.trading.market_data.model.OrderBookKey;
import com.hope.trading.market_data.model.OrderBookSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@Slf4j
public class OrderBookEventPublisher {
    private final Sinks.Many<OrderBookSnapshot> sink =
            Sinks.many()
                    .multicast()
                    .directBestEffort();
    private final ConcurrentMap<OrderBookKey, OrderBookSnapshot> latestSnapshots =
            new ConcurrentHashMap<>();

    public void publish(OrderBookSnapshot snapshot) {
        OrderBookKey key =
                new OrderBookKey(snapshot.marketId(), snapshot.depth());
        latestSnapshots.put(key, snapshot);

        Sinks.EmitResult result = sink.tryEmitNext(snapshot);

        if (result.isFailure()
                && result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
            log.warn(
                    "Unable to publish order-book snapshot marketId={} depth={} result={}",
                    snapshot.marketId(),
                    snapshot.depth(),
                    result
            );
        }
    }

    public Flux<OrderBookSnapshot> streamByMarketAndDepth(
            UUID marketId,
            int depth
    ) {
        OrderBookKey key = new OrderBookKey(marketId, depth);
        Flux<OrderBookSnapshot> current = Flux.defer(() -> {
            OrderBookSnapshot snapshot = latestSnapshots.get(key);
            return snapshot == null
                    ? Flux.empty()
                    : Flux.just(snapshot);
        });
        Flux<OrderBookSnapshot> updates = sink.asFlux()
                .filter(snapshot ->
                        snapshot.marketId().equals(marketId)
                                && snapshot.depth() == depth
                );

        return Flux.concat(current, updates);
    }

    public void clear(OrderBookKey key) {
        latestSnapshots.remove(key);
    }
}
