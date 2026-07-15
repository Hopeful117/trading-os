package com.hope.trading.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class LoggingFilter implements GlobalFilter , Ordered {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var request = exchange.getRequest();
        log.info("Incoming request: {} {}", request.getMethod(), request.getURI());
        return chain.filter(exchange)
                .doOnSuccess(aVoid -> {
                    var response = exchange.getResponse();
                    log.info("Outgoing response: {} {}", response.getStatusCode(), request.getURI());
                })
                .doOnError(error-> log.error("Error processing request: {} {}", request.getMethod(), request.getURI(), error));
    }
    @Override
    public int getOrder() {
        return -1;
    }
}
