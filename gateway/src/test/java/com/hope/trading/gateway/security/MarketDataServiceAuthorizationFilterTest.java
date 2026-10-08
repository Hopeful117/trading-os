package com.hope.trading.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MarketDataServiceAuthorizationFilterTest {
    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void delegatesSubscriptionMutationWithServiceCredential() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/markets/market-id/subscriptions")
                        .header("X-Service-Authorization", "Bearer client-value")
                        .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = value -> {
            forwarded.set(value);
            value.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
            return value.getResponse().setComplete();
        };

        new MarketDataServiceAuthorizationFilter(SECRET).filter(exchange, chain).block();

        String header = forwarded.get().getRequest().getHeaders().getFirst("X-Service-Authorization");
        assertThat(header).startsWith("Bearer ").isNotEqualTo("Bearer client-value");
    }

    @Test
    void doesNotAddServiceCredentialToPublicMarketReads() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/markets/market-id").build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = value -> {
            forwarded.set(value);
            value.getResponse().setStatusCode(HttpStatus.OK);
            return value.getResponse().setComplete();
        };

        new MarketDataServiceAuthorizationFilter(SECRET).filter(exchange, chain).block();

        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-Service-Authorization"))
                .isNull();
    }
}
