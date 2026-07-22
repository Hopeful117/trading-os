package com.hope.trading.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class LoggingFilter implements GlobalFilter , Ordered {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var request = exchange.getRequest();
        long startedAt = System.nanoTime();
        log.debug("HTTP request started method={} path={}", request.getMethod(), request.getPath());
        return chain.filter(exchange)
                .doOnSuccess(aVoid -> {
                    var response = exchange.getResponse();
                    log.info("HTTP request completed method={} path={} status={} durationMs={}",
                            request.getMethod(), request.getPath(), response.getStatusCode(), elapsedMillis(startedAt));
                })
                .doOnError(error -> log.error("HTTP request failed method={} path={} durationMs={}",
                        request.getMethod(), request.getPath(), elapsedMillis(startedAt), error));
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
    @Override
    public int getOrder() {
        return -1;
    }
}
