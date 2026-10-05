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
    private HttpServer newsStub;
    private final CopyOnWriteArrayList<String> intelligenceRequests =
            new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> tradingCoreRequests =
            new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> newsRequests =
            new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> intelligenceActorHeaders =
            new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> tradingCoreIdempotencyHeaders =
            new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> tradingCoreAuthorizationHeaders =
            new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> tradingCoreBodies =
            new CopyOnWriteArrayList<>();
    private int intelligencePort;
    private int tradingCorePort;
    private int newsPort;
    private ConfigurableApplicationContext gateway;
    private int gatewayPort;
    private final HttpClient client = HttpClient.newHttpClient();
    private static final UUID AUTHENTICATED_USER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void startStubsAndGateway() throws Exception {
        intelligenceStub = stub(intelligenceRequests, "X-Actor-Id", intelligenceActorHeaders,
                null, null, "intelligence");
        intelligenceStub.start();
        intelligencePort = intelligenceStub.getAddress().getPort();

        tradingCoreStub = stub(tradingCoreRequests, "Idempotency-Key", tradingCoreIdempotencyHeaders,
                tradingCoreAuthorizationHeaders, tradingCoreBodies, "trading-core");
        tradingCoreStub.start();
        tradingCorePort = tradingCoreStub.getAddress().getPort();

        newsStub = stub(newsRequests, null, null, null, null, "news");
        newsStub.start();
        newsPort = newsStub.getAddress().getPort();

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
                                        "gateway.targets.news-service",
                                                "http://127.0.0.1:" + newsPort,
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
        if (gateway != null) gateway.close();
        if (intelligenceStub != null) intelligenceStub.stop(0);
        if (tradingCoreStub != null) tradingCoreStub.stop(0);
        if (newsStub != null) newsStub.stop(0);
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
        String idempotencyKey = "trade-plan-key-0075";
        String body = "{\"accountId\":\"" + AUTHENTICATED_USER_ID + "\"}";

        HttpResponse<String> response = send("POST", path,
                java.util.Map.of("Idempotency-Key", idempotencyKey), body);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(tradingCoreRequests).containsExactly("POST " + path);
        assertThat(tradingCoreIdempotencyHeaders).containsExactly(idempotencyKey);
        assertThat(tradingCoreBodies).containsExactly(body);
        assertThat(intelligenceRequests)
                .as("historical WRONG_SERVICE regression: intelligence must not "
                        + "receive public trade-plan creation")
                .isEmpty();
    }

    @Test
    void executionValidationTravelsThroughGatewayAndPreservesIdempotencyKey()
            throws Exception {
        String idempotencyKey = "execution-key-0075";

        String body = "{\"tradePlanId\":\"00000000-0000-0000-0000-000000000001\","
                + "\"tradePlanVersion\":1,"
                + "\"evaluationId\":\"00000000-0000-0000-0000-000000000002\","
                + "\"brokerAccountId\":\"00000000-0000-0000-0000-000000000003\","
                + "\"expiresAt\":\"2099-01-01T00:00:00Z\"}";

        HttpResponse<String> response = send("POST", "/api/v1/executions/validate",
                java.util.Map.of("Idempotency-Key", idempotencyKey), body);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).isEqualTo("{\"id\":\"execution-0075\"}");
        assertThat(tradingCoreRequests).containsExactly("POST /api/v1/executions/validate");
        assertThat(tradingCoreIdempotencyHeaders).containsExactly(idempotencyKey);
        assertThat(tradingCoreAuthorizationHeaders).hasSize(1);
        assertThat(tradingCoreAuthorizationHeaders.get(0).startsWith("Bearer ")).isTrue();
        assertThat(tradingCoreBodies).containsExactly(body);
    }

    @Test
    void economicCalendarReadTravelsThroughGatewayToNewsService()
            throws Exception {
        HttpResponse<String> response = send("GET", "/api/v1/news/events");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(newsRequests).containsExactly("GET /api/v1/news/events");
        assertThat(response.body()).isEqualTo(
                "{\"status\":\"AVAILABLE\",\"items\":[],"
                        + "\"fetchedAt\":\"2026-10-05T00:00:00Z\","
                        + "\"message\":null,\"attribution\":\"Data: XOOMAR (https://xoomar.com/markets/api/calendar)\"}");
    }

    @Test
    void clientActorHeaderIsReplacedByAuthenticatedIdentityOnIntelligenceRoutes()
            throws Exception {
        HttpResponse<String> response = send("GET", "/api/v1/intelligence/scans/whatever",
                java.util.Map.of("X-Actor-Id", "attacker-controlled-actor"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(intelligenceActorHeaders).containsExactly(AUTHENTICATED_USER_ID.toString());
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
        return send(method, path, java.util.Map.of(), "{}");
    }

    private HttpResponse<String> send(String method, String path,
            java.util.Map<String, String> headers) throws Exception {
        return send(method, path, headers, "{}");
    }

    private HttpResponse<String> send(String method, String path,
            java.util.Map<String, String> headers, String body) throws Exception {
        var jwtService = gateway.getBean(
                com.hope.trading.gateway.security.JwtService.class);
        String token = jwtService.generateToken(UserAuthenticationDto.builder()
                .userId(AUTHENTICATED_USER_ID)
                .username("trader")
                .email("trader@example.com")
                .role(com.hope.trading.gateway.helper.Role.ROLE_USER)
                .build());
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + gatewayPort + path))
                .header("Authorization", "Bearer " + token)
                .method(method, method.equals("POST")
                        ? HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)
                        : HttpRequest.BodyPublishers.noBody());
        headers.forEach(requestBuilder::header);
        HttpRequest request = requestBuilder.build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpServer stub(List<String> recorded, String headerName,
            List<String> recordedHeaders, List<String> authorizationHeaders,
            List<String> recordedBodies, String service) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String request = exchange.getRequestMethod() + " " + path;
            recorded.add(request);
            String requestBody = new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8);
            if (recordedBodies != null) recordedBodies.add(requestBody);
            if (headerName != null && recordedHeaders != null) {
                String headerValue = exchange.getRequestHeaders().getFirst(headerName);
                recordedHeaders.add(headerValue == null ? "<absent>" : headerValue);
            }
            if (authorizationHeaders != null) {
                authorizationHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
            }
            int status = 200;
            String response = "[]";
            if ("trading-core".equals(service) && path.contains("/trade-plans/analyses/")) {
                if (!requestBody.contains("accountId")
                        || exchange.getRequestHeaders().getFirst("Idempotency-Key") == null) {
                    status = 400;
                    response = "{\"error\":\"invalid trade-plan request\"}";
                }
            } else if ("trading-core".equals(service) && path.equals("/api/v1/executions/validate")) {
                boolean complete = requestBody.contains("tradePlanId")
                        && requestBody.contains("tradePlanVersion")
                        && requestBody.contains("evaluationId")
                        && requestBody.contains("brokerAccountId")
                        && requestBody.contains("expiresAt")
                        && exchange.getRequestHeaders().getFirst("Idempotency-Key") != null;
                status = complete ? 201 : 400;
                response = complete ? "{\"id\":\"execution-0075\"}"
                        : "{\"error\":\"invalid execution request\"}";
            } else if ("news".equals(service) && path.equals("/api/v1/news/events")) {
                response = "{\"status\":\"AVAILABLE\",\"items\":[],"
                        + "\"fetchedAt\":\"2026-10-05T00:00:00Z\","
                        + "\"message\":null,\"attribution\":\"Data: XOOMAR (https://xoomar.com/markets/api/calendar)\"}";
            } else if ("news".equals(service)) {
                status = 404;
                response = "{\"error\":\"not found\"}";
            }
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        return server;
    }
}
