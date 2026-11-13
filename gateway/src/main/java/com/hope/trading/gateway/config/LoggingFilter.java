package com.hope.trading.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class LoggingFilter implements GlobalFilter , Ordered {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestedCorrelationId = exchange.getRequest().getHeaders().getFirst("X-Correlation-ID");
        String correlationId = requestedCorrelationId == null || requestedCorrelationId.isBlank()
                ? UUID.randomUUID().toString() : requestedCorrelationId.strip();
        if (requestedCorrelationId == null || requestedCorrelationId.isBlank()) {
            exchange = exchange.mutate().request(request -> request.headers(headers ->
                    headers.set("X-Correlation-ID", correlationId))).build();
        }
        exchange.getResponse().getHeaders().set("X-Correlation-ID", correlationId);
        var request = exchange.getRequest();
        boolean credentialCommand = request.getPath().value().matches(
                "^/api/v1/broker-accounts/[^/]+/(credentials|validate).*$");
        if (credentialCommand) {
            exchange.getResponse().getHeaders().setCacheControl("no-store");
        }
        long startedAt = System.nanoTime();
        log.debug("HTTP request started method={} path={}", request.getMethod(), request.getPath());
        var currentExchange = exchange;
        return chain.filter(currentExchange)
                .doOnSuccess(aVoid -> {
                    var response = currentExchange.getResponse();
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
