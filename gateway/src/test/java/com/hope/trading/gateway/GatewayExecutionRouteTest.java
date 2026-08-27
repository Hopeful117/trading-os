package com.hope.trading.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;

@SpringBootTest
@ActiveProfiles("test")
class GatewayExecutionRouteTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void executionRouteForwardsToTradingCore() {
        Route route = route("executions");

        assertThat(route).isNotNull();
        assertThat(route.getUri()).isEqualTo(URI.create("lb://trading-core"));
        assertThat(matches(route, HttpMethod.POST, "/api/v1/executions/validate"))
                .as("execution validate must match the executions route").isTrue();
    }

    @Test
    void executionRouteMatchesExecuteEndpoint() {
        Route route = route("executions");

        assertThat(route).isNotNull();
        assertThat(matches(route, HttpMethod.POST,
                "/api/v1/executions/11111111-1111-1111-1111-111111111111/execute"))
                .as("execution execute must match the executions route").isTrue();
    }

    @Test
    void executionRouteDoesNotCatchUnrelatedPaths() {
        Route route = route("executions");

        assertThat(route).isNotNull();
        assertThat(matches(route, HttpMethod.GET, "/api/v1/accounts"))
                .as("unrelated account path must not match the executions route").isFalse();
    }

    @Test
    void existingRoutesRemainRegistered() {
        List<String> ids = Flux.from(routeLocator.getRoutes()).map(Route::getId).collectList().block();
        assertThat(ids).contains(
                "authentication",
                "accounts",
                "broker-credential-commands",
                "broker-accounts",
                "trade-plan-risk-evaluations",
                "markets",
                "market-intelligence",
                "executions"
        );
    }

    private Route route(String id) {
        return Flux.from(routeLocator.getRoutes()).filter(route -> id.equals(route.getId()))
                .next().block();
    }

    private boolean matches(Route route, HttpMethod method, String path) {
        MockServerHttpRequest request = MockServerHttpRequest.method(method, path).build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        return Boolean.TRUE.equals(Flux.from(route.getPredicate().apply(exchange)).blockLast());
    }
}
