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
                "challenge-lifecycle",
                "challenge-account",
                "accounts",
                "trades",
                "broker-credential-commands",
                "ctrader-authorization",
                "broker-accounts",
                "trade-plan-risk-evaluations",
                "markets",
                "market-intelligence",
                "executions",
                "risk-profile-catalog"
        );
    }

    @Test
    void ctraderAuthorizationRoutesToBrokerServiceBeforeBroadBrokerAccountRoute() {
        Route route = route("ctrader-authorization");

        assertThat(route).isNotNull();
        assertThat(route.getUri()).isEqualTo(URI.create("lb://broker-service"));
        assertThat(matches(route, HttpMethod.POST,
                "/api/v1/broker-accounts/4075e131-45ee-4ff5-85b7-052ac5ede330/ctrader/authorization"))
                .as("cTrader authorization initiation must route to Broker Service").isTrue();
        assertThat(matches(route, HttpMethod.GET,
                "/api/v1/broker-accounts/ctrader/authorization/callback"))
                .as("cTrader authorization callback must route to Broker Service").isTrue();
        assertThat(matches(route, HttpMethod.POST,
                "/api/v1/broker-accounts/4075e131-45ee-4ff5-85b7-052ac5ede330/ctrader/authorization/11111111-1111-1111-1111-111111111111/binding"))
                .as("cTrader binding must route to Broker Service").isTrue();
    }

    @Test
    void challengeCatalogRouteForwardsToTradingCore() {
        Route route = route("challenge-lifecycle");

        assertThat(route).isNotNull();
        assertThat(route.getUri()).isEqualTo(URI.create("lb://trading-core"));
        assertThat(matches(route, HttpMethod.GET, "/api/v1/challenges"))
                .as("challenge catalog must match the Trading Core route").isTrue();
        assertThat(matches(route, HttpMethod.GET, "/api/v1/challenges/"))
                .as("challenge catalog subpaths must match the route").isTrue();
    }

    @Test
    void accountChallengeRouteMatchesStartAndProgress() {
        Route route = route("challenge-account");

        assertThat(route).isNotNull();
        assertThat(route.getUri()).isEqualTo(URI.create("lb://trading-core"));
        String path = "/api/v1/accounts/11111111-1111-1111-1111-111111111111/challenge";
        assertThat(matches(route, HttpMethod.POST, path))
                .as("challenge start must match the Trading Core route").isTrue();
        assertThat(matches(route, HttpMethod.GET, path))
                .as("challenge progress must match the Trading Core route").isTrue();
    }

    @Test
    void accountChallengeRouteDoesNotCatchUnrelatedAccountPaths() {
        Route route = route("challenge-account");

        assertThat(route).isNotNull();
        assertThat(matches(route, HttpMethod.GET,
                "/api/v1/accounts/11111111-1111-1111-1111-111111111111"))
                .as("plain account paths must use the account route instead").isFalse();
        assertThat(matches(route, HttpMethod.GET,
                "/api/v1/accounts/11111111-1111-1111-1111-111111111111/challenges"))
                .as("unrelated account paths must not match the challenge route").isFalse();
    }

    @Test
    void v2AccountSynchronizationRouteForwardsToTradingCore() {
        Route route = route("accounts");

        assertThat(route).isNotNull();
        assertThat(route.getUri()).isEqualTo(URI.create("lb://trading-core"));
        assertThat(matches(route, HttpMethod.POST,
                "/api/v2/accounts/4075e131-45ee-4ff5-85b7-052ac5ede330/synchronize"))
                .as("v2 account synchronization must match the Trading Core route").isTrue();
    }

    @Test
    void existingV1AccountRouteRemainsOwnedByTradingCore() {
        Route route = route("accounts");

        assertThat(route).isNotNull();
        assertThat(matches(route, HttpMethod.GET,
                "/api/v1/accounts/11111111-1111-1111-1111-111111111111"))
                .as("v1 account lookup must remain on the Trading Core route").isTrue();
    }

    @Test
    void accountChallengeRouteWinsOverBroadAccountRoute() {
        String path = "/api/v1/accounts/11111111-1111-1111-1111-111111111111/challenge";
        MockServerHttpRequest request = MockServerHttpRequest.post(path).build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        String selectedRoute = Flux.from(routeLocator.getRoutes())
                .filter(route -> Boolean.TRUE.equals(Flux.from(route.getPredicate()
                        .apply(exchange)).blockLast()))
                .map(Route::getId)
                .next()
                .block();

        assertThat(selectedRoute).isEqualTo("challenge-account");
    }

    @Test
    void challengeLifecycleRouteDoesNotCatchInternalPaths() {
        Route route = route("challenge-lifecycle");

        assertThat(route).isNotNull();
        assertThat(matches(route, HttpMethod.GET, "/internal/v1/challenges"))
                .as("internal challenge paths must not match a public route").isFalse();
    }

    @Test
    void riskProfileCatalogRouteForwardsToTradingCore() {
        Route route = route("risk-profile-catalog");

        assertThat(route).isNotNull();
        assertThat(route.getUri()).isEqualTo(URI.create("lb://trading-core"));
        assertThat(matches(route, HttpMethod.GET, "/api/v1/risk-profiles/eligible"))
                .as("risk profile catalog must match the Trading Core route").isTrue();
    }

    @Test
    void tradesRouteForwardsToTradingCore() {
        Route route = route("trades");

        assertThat(route).isNotNull();
        assertThat(route.getUri()).isEqualTo(URI.create("lb://trading-core"));
        assertThat(matches(route, HttpMethod.POST, "/api/v1/trades"))
                .as("trade creation must match the Trading Core route").isTrue();
        assertThat(matches(route, HttpMethod.GET, "/api/v1/trades?accountId=11111111-1111-1111-1111-111111111111"))
                .as("trade listing must match the Trading Core route").isTrue();
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
