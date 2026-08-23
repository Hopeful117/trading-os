package com.hope.trading.gateway;

import com.hope.trading.gateway.dto.UserAuthenticationDto;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * STORY-0019 integration proof: a real HTTP request travels through the real
 * Gateway application and lands on the expected downstream service with the
 * expected forwarded path.
 *
 * <p>Downstream services are replaced by local stub HTTP servers and the
 * Gateway route targets are overridden through {@code gateway.targets.*}
 * (production configuration code, no test-specific routing). This fails if
 * the public paths stop being routed correctly.</p>
 */
class GatewayDownstreamRoutingIntegrationTest {

    private HttpServer intelligenceStub;
    private HttpServer tradingCoreStub;
    private final CopyOnWriteArrayList<String> intelligenceRequests =
            new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> tradingCoreRequests =
            new CopyOnWriteArrayList<>();
    private int intelligencePort;
    private int tradingCorePort;
    private ConfigurableApplicationContext gateway;
    private int gatewayPort;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void startStubsAndGateway() throws Exception {
        intelligenceStub = stub(intelligenceRequests);
        intelligenceStub.start();
        intelligencePort = intelligenceStub.getAddress().getPort();

        tradingCoreStub = stub(tradingCoreRequests);
        tradingCoreStub.start();
        tradingCorePort = tradingCoreStub.getAddress().getPort();

        gatewayPort = freePort();
        // Valid base64 secret so this test can mint tokens.
        String jwtSecret = "YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFh"
                + "YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFh";
        gateway = new SpringApplicationBuilder(GatewayApplication.class)
                .profiles("test")
                .initializers(context -> context.getEnvironment().getPropertySources()
                        .addFirst(new org.springframework.core.env.MapPropertySource(
                                "story-0019-overrides",
                                java.util.Map.of(
                                        "server.port", String.valueOf(gatewayPort),
                                        "gateway.targets.market-intelligence",
                                                "http://127.0.0.1:" + intelligencePort,
                                        "gateway.targets.trading-core",
                                                "http://127.0.0.1:" + tradingCorePort,
                                        "spring.cloud.gateway.discovery.locator.enabled",
                                                "false",
                                        "security.jwt.secret", jwtSecret))))
                .run();
    }

    private static int freePort() throws Exception {
        try (var socket = new java.net.ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    @AfterEach
    void stopAll() {
        gateway.close();
        intelligenceStub.stop(0);
        tradingCoreStub.stop(0);
    }

    @Test
    void opportunityRequestTravelsThroughGatewayToMarketIntelligence()
            throws Exception {
        HttpResponse<String> response = send("GET",
                "/api/v1/opportunities/active");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(intelligenceRequests).containsExactly(
                "GET /api/v1/opportunities/active");
        assertThat(tradingCoreRequests).isEmpty();
    }

    @Test
    void publicTradePlanCreationRoutesToTradingCoreNotMarketIntelligence()
            throws Exception {
        String path = "/api/v1/trade-plans/analyses/" + UUID.randomUUID()
                + "/trade-plans";

        HttpResponse<String> response = send("POST", path);

        // The stub downstream answers 200; what matters here is WHICH service
        // received the request and with which forwarded path.
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(tradingCoreRequests).containsExactly("POST " + path);
        assertThat(intelligenceRequests)
                .as("historical WRONG_SERVICE regression: intelligence must not "
                        + "receive public trade-plan creation")
                .isEmpty();
    }

    @Test
    void scanRequestStillRoutesToMarketIntelligence() throws Exception {
        HttpResponse<String> response = send("GET", "/api/v1/intelligence/scans/whatever");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(intelligenceRequests).containsExactly(
                "GET /api/v1/intelligence/scans/whatever");
    }

    // ---- helpers -----------------------------------------------------------

    /** Authenticated request: the gateway JWT filter protects these paths. */
    private HttpResponse<String> send(String method, String path) throws Exception {
        var jwtService = gateway.getBean(
                com.hope.trading.gateway.security.JwtService.class);
        String token = jwtService.generateToken(UserAuthenticationDto.builder()
                .userId(UUID.randomUUID())
                .username("trader")
                .email("trader@example.com")
                .role(com.hope.trading.gateway.helper.Role.ROLE_USER)
                .build());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + gatewayPort + path))
                .header("Authorization", "Bearer " + token)
                .method(method, method.equals("POST")
                        ? HttpRequest.BodyPublishers.ofString("{}", StandardCharsets.UTF_8)
                        : HttpRequest.BodyPublishers.noBody())
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpServer stub(List<String> recorded) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            recorded.add(exchange.getRequestMethod() + " "
                    + exchange.getRequestURI().getPath());
            byte[] body = "[]".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        return server;
    }
}
