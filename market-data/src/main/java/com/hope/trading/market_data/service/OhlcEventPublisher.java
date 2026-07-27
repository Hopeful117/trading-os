package com.hope.trading.market_data.service;

import com.hope.trading.market_data.model.OhlcEvent;
import com.hope.trading.market_data.model.OhlcInterval;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.UUID;

@Component
@Slf4j
public class OhlcEventPublisher {
    private final Sinks.Many<OhlcEvent> sink =
            Sinks.many()
                    .multicast()
                    .directBestEffort();

    public void publish(OhlcEvent event) {
        Sinks.EmitResult result =
                sink.tryEmitNext(event);
        log.info(
                "[OHLC-PUBLISHER] marketId={} interval={} close={} result={}",
                event.marketId(),
                event.interval(),
                event.close(),
                result
        );

        if (result.isFailure()
                && result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
            log.warn(
                    "Unable to publish OHLC event symbol={} result={}",
                    event.symbol(),
                    result
            );
        }
    }

    public Flux<OhlcEvent> streamByMarketAndInterval(
            UUID marketId,
            OhlcInterval interval
    ) {
        return sink.asFlux()
                .filter(event ->
                        event.marketId().equals(marketId)
                                && event.interval() == interval
                );
    }
}
