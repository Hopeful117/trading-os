package com.hope.trading.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Flux;

/**
 * STORY-0019: public Market Intelligence routing contract.
 *
 * <p>Proves the trader-journey paths reach the right downstream service and
 * that the historical WRONG_SERVICE collision (public trade-plan creation
 * captured by the intelligence route) is gone.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class GatewayMarketIntelligenceRouteTest {

    private static final UUID ANALYSIS_ID = UUID.randomUUID();

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void scanRouteGoesToMarketIntelligence() {
        Route route = route("market-intelligence");
        assertThat(route.getUri()).isEqualTo(URI.create("lb://market-intelligence"));
        assertThat(matches(route, HttpMethod.POST, "/api/v1/intelligence/scans")).isTrue();
    }

    @Test
    void opportunityListRoutesToMarketIntelligence() {
        Route route = matchedRoute(HttpMethod.GET, "/api/v1/opportunities");
        assertThat(route.getId()).isEqualTo("opportunities");
        assertThat(route.getUri()).isEqualTo(URI.create("lb://market-intelligence"));
    }

    @Test
    void opportunityActiveRoutesToMarketIntelligence() {
        Route route = matchedRoute(HttpMethod.GET, "/api/v1/opportunities/active");
        assertThat(route.getId()).isEqualTo("opportunities");
    }

    @Test
    void opportunityDetailRoutesToMarketIntelligence() {
        Route route = matchedRoute(HttpMethod.GET,
                "/api/v1/opportunities/" + UUID.randomUUID());
        assertThat(route.getId()).isEqualTo("opportunities");
    }

    @Test
    void opportunityHistoryRoutesToMarketIntelligence() {
        Route route = matchedRoute(HttpMethod.GET,
                "/api/v1/opportunities/history/" + UUID.randomUUID());
        assertThat(route.getId()).isEqualTo("opportunities");
    }

    @Test
    void publicTradePlanCreationRoutesToTradingCoreNotMarketIntelligence() {
        String path = "/api/v1/trade-plans/analyses/" + ANALYSIS_ID + "/trade-plans";

        Route tradingCore = matchedRoute(HttpMethod.POST, path);
        assertThat(tradingCore.getId()).isEqualTo("trade-plan-risk-evaluations");
        assertThat(tradingCore.getUri()).isEqualTo(URI.create("lb://trading-core"));

        // The historical WRONG_SERVICE regression: the intelligence route must
        // not capture the public TradePlan creation path anymore.
        Route intelligence = route("market-intelligence");
        assertThat(matches(intelligence, HttpMethod.POST, path)).isFalse();
    }

    @Test
    void internalMarketIntelligenceEndpointsAreNotRouted() {
        String internalGeneration =
                "/internal/v1/intelligence/analyses/" + ANALYSIS_ID + "/trade-plans";
        String internalRisk =
                "/internal/v1/trade-plans/" + UUID.randomUUID()
                        + "/versions/1/risk-validation-snapshot";
        for (String path : List.of(internalGeneration, internalRisk)) {
            assertThat(matchedRouteOrNull(HttpMethod.POST, path))
                    .as("internal path %s must stay unreachable", path)
                    .isNull();
            assertThat(matchedRouteOrNull(HttpMethod.GET, path))
                    .as("internal path %s must stay unreachable", path)
                    .isNull();
        }
    }

    // ---- helpers -----------------------------------------------------------

    private Route route(String id) {
        return Flux.from(routeLocator.getRoutes())
                .filter(candidate -> id.equals(candidate.getId()))
                .next().block();
    }

    private Route matchedRoute(HttpMethod method, String path) {
        Route route = matchedRouteOrNull(method, path);
        assertThat(route).as("no route matches %s %s", method, path).isNotNull();
        return route;
    }

    private boolean matches(Route route, HttpMethod method, String path) {
        MockServerHttpRequest request = MockServerHttpRequest.method(method, path).build();
        var exchange = MockServerWebExchange.from(request);
        return Boolean.TRUE.equals(
                Flux.from(route.getPredicate().apply(exchange)).blockLast());
    }

    private Route matchedRouteOrNull(HttpMethod method, String path) {
        // Exchanges are single-use; build a fresh one per candidate route.
        return Flux.from(routeLocator.getRoutes())
                .filter(route -> {
                    MockServerHttpRequest request =
                            MockServerHttpRequest.method(method, path).build();
                    var exchange = MockServerWebExchange.from(request);
                    return Boolean.TRUE.equals(Flux
                            .from(route.getPredicate().apply(exchange)).blockLast());
                })
                .next().block();
    }
}
