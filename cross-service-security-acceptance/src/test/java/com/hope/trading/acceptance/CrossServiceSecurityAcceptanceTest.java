package com.hope.trading.acceptance;

import com.hope.trading.broker_service.BrokerServiceApplication;
import com.hope.trading.broker_service.security.BrokerPrincipal;
import com.hope.trading.market_intelligence.MarketIntelligenceApplication;
import com.hope.trading.market_intelligence.security.MiServicePrincipal;
import com.hope.trading.trading_core.TradingCoreApplication;
import com.hope.trading.trading_core.config.BrokerServiceFeignConfiguration;
import com.hope.trading.trading_core.config.MarketIntelligenceFeignConfiguration;
import com.hope.trading.trading_core.security.ServiceJwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CrossServiceSecurityAcceptanceTest {
    private static final String USER_KEY = "QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=";
    private static final String BROKER_KEY = USER_KEY;
    private static final String MI_KEY = "YmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJi";
    private static final UUID USER_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static ConfigurableApplicationContext broker, mi, core;
    private static int brokerPort, miPort, corePort;
    private static HttpClient http;

    @BeforeAll
    static void start() {
        broker = app(BrokerServiceApplication.class, BrokerProbe.class, brokerProperties()).run();
        brokerPort = port(broker);
        mi = app(MarketIntelligenceApplication.class, MiProbe.class, miProperties()).run();
        miPort = port(mi);
        core = app(TradingCoreApplication.class, CoreProbe.class, CoreClients.class, coreProperties()).run();
        corePort = port(core);
        http = HttpClient.newHttpClient();
    }

    @AfterAll
    static void stop() {
        if (core != null) core.close();
        if (mi != null) mi.close();
        if (broker != null) broker.close();
    }

    @Test
    void realCoreHttpIngressPerformsRealFeignCallsToBothReceivers() throws Exception {
        HttpResponse<String> response = request(corePort, "/harness/core/probe", userToken(USER_A), "Authorization", "GET", null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("trading-core", USER_A.toString());
    }

    @Test
    void receiverAudienceIsolationHoldsAcrossRealSockets() throws Exception {
        assertThat(serviceRequest(miPort, "/internal/v1/harness/principal", token("broker-service", USER_A, BROKER_KEY, "trading-core")).statusCode()).isEqualTo(401);
        assertThat(serviceRequest(brokerPort, "/internal/v1/harness/principal", token("market-intelligence", USER_A, MI_KEY, "trading-core")).statusCode()).isEqualTo(401);
    }

    @Test
    void userAndServiceTrustClassesRemainSeparatedAtRuntime() throws Exception {
        assertThat(request(miPort, "/internal/v1/harness/principal", userToken(USER_A), "Authorization", "GET", null).statusCode()).isEqualTo(401);
        assertThat(request(miPort, "/api/v1/opportunities/active", token("market-intelligence", null, MI_KEY, "trading-core"), "Authorization", "GET", null).statusCode()).isEqualTo(401);
        assertThat(serviceRequest(miPort, "/internal/trade-planning/metrics", token("market-intelligence", null, MI_KEY, "trading-core")).statusCode()).isEqualTo(401);
    }

    @Test
    void validServiceWithoutDelegationCannotPerformUserOperation() throws Exception {
        HttpResponse<String> response = serviceRequest(miPort, "/internal/v1/trade-plans/" + UUID.randomUUID() + "/versions/1/decisions", token("market-intelligence", null, MI_KEY, "trading-core"), "POST", "{\"actorId\":\"" + USER_A + "\",\"decision\":\"ACCEPT\"}");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(403);
    }

    @Test
    void compatibilityActorMismatchIsRejectedByRealMiApplication() throws Exception {
        HttpResponse<String> response = serviceRequest(miPort, "/internal/v1/trade-plans/" + UUID.randomUUID() + "/versions/1/decisions", token("market-intelligence", USER_A, MI_KEY, "trading-core"), "POST", "{\"actorId\":\"" + USER_B + "\",\"decision\":\"ACCEPT\"}");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(403);
    }

    @Test
    void riskHandoffCanReachRealMiWithoutFakeActor() throws Exception {
        assertThat(serviceRequest(miPort, "/internal/v1/trade-plans/" + UUID.randomUUID() + "/versions/1/risk-validation-snapshot", token("market-intelligence", null, MI_KEY, "trading-core")).statusCode()).isEqualTo(404);
    }

    @Test
    void unauthorizedButCorrectlySignedCallerIsRejected() throws Exception {
        HttpResponse<String> response = serviceRequest(miPort, "/internal/v1/harness/principal", token("market-intelligence", null, MI_KEY, "other-service"));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(403);
    }

    @Test
    void wrongKeyIsRejectedIndependentlyFromAudience() throws Exception {
        assertThat(serviceRequest(miPort, "/internal/v1/harness/principal", token("market-intelligence", null, BROKER_KEY, "trading-core")).statusCode()).isEqualTo(401);
    }

    private static SpringApplicationBuilder app(Class<?> main, Class<?> source, Map<String, Object> props) {
        props.forEach((key, value) -> System.setProperty(key, String.valueOf(value)));
        return new SpringApplicationBuilder(main).sources(source).web(WebApplicationType.SERVLET).properties(props);
    }
    private static SpringApplicationBuilder app(Class<?> main, Class<?> source, Class<?> source2, Map<String, Object> props) {
        props.forEach((key, value) -> System.setProperty(key, String.valueOf(value)));
        return new SpringApplicationBuilder(main).sources(source, source2).web(WebApplicationType.SERVLET).properties(props);
    }
    private static int port(ConfigurableApplicationContext c) { return ((org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext) c).getWebServer().getPort(); }
    private static HttpResponse<String> serviceRequest(int port, String path, String token) throws Exception { return request(port, path, token, "X-Service-Authorization", "GET", null); }
    private static HttpResponse<String> serviceRequest(int port, String path, String token, String method, String body) throws Exception { return request(port, path, token, "X-Service-Authorization", method, body); }
    private static HttpResponse<String> request(int port, String path, String token, String header, String method, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header(header, token).header("Content-Type", "application/json");
        return http.send("POST".equals(method) ? b.POST(HttpRequest.BodyPublishers.ofString(body)).build() : b.GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private static String userToken(UUID user) { return "Bearer " + Jwts.builder().subject(user.toString()).issuer("trading-os-test").claim("username", "runtime-user").claim("email", "runtime@example.com").claim("role", "ROLE_USER").issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 60000)).signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(USER_KEY))).compact(); }
    private static String token(String audience, UUID actor, String secret, String issuer) { var b = Jwts.builder().issuer(issuer).subject(issuer).audience().add(audience).and().claim("type", "service").issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 60000)).id(UUID.randomUUID().toString()); if (actor != null) b.claim("actor_id", actor.toString()); return "Bearer " + b.signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))).compact(); }
    private static Map<String, Object> base(String db) { return Map.ofEntries(Map.entry("server.port", 0), Map.entry("eureka.client.enabled", false), Map.entry("spring.cloud.discovery.enabled", false), Map.entry("spring.flyway.enabled", false), Map.entry("spring.datasource.url", db), Map.entry("spring.datasource.driver-class-name", "org.h2.Driver"), Map.entry("spring.jpa.hibernate.ddl-auto", "create-drop"), Map.entry("security.jwt.secret", USER_KEY), Map.entry("security.jwt.issuer", "trading-os-test")); }
    private static Map<String, Object> brokerProperties() { var p = new java.util.HashMap<>(base("jdbc:h2:mem:broker;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")); p.putAll(Map.ofEntries(Map.entry("trading-os.broker.credentials.source", "environment"), Map.entry("trading-os.secrets.master-key", USER_KEY), Map.entry("kraken.base-url", "https://example.invalid"), Map.entry("kraken.connect-timeout", "1s"), Map.entry("kraken.read-timeout", "1s"), Map.entry("kraken.api-key", "test"), Map.entry("kraken.api-secret", "dGVzdA=="), Map.entry("security.service-jwt.enabled", true), Map.entry("security.service-jwt.name", "broker-service"), Map.entry("security.service-jwt.secret", BROKER_KEY), Map.entry("security.service-jwt.trusted.trading-core", BROKER_KEY), Map.entry("security.service-jwt.authorized-callers", "trading-core"))); return p; }
    private static Map<String, Object> miProperties() { var p = new java.util.HashMap<>(base("jdbc:h2:mem:mi;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")); p.putAll(Map.of("security.service-jwt.enabled", true, "security.service-jwt.audience", "market-intelligence", "security.service-jwt.authorized-callers", "trading-core", "security.service-jwt.trusted.trading-core", MI_KEY, "security.service-jwt.trusted.other-service", MI_KEY)); return p; }
    private static Map<String, Object> coreProperties() { var p = new java.util.HashMap<>(base("jdbc:h2:mem:core;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")); p.putAll(Map.of("security.service-jwt.enabled", true, "security.service-jwt.name", "trading-core", "security.service-jwt.secret", BROKER_KEY, "security.service-jwt.audience-secrets.market-intelligence", MI_KEY, "broker-service.base-url", "http://localhost:" + brokerPort, "spring.cloud.openfeign.client.config.market-intelligence.url", "http://localhost:" + miPort)); return p; }

    @Configuration @EnableFeignClients(clients = {BrokerClient.class, MiClient.class}) static class CoreClients { }
    @FeignClient(name = "broker-probe", url = "${broker-service.base-url}", configuration = BrokerServiceFeignConfiguration.class) interface BrokerClient { @GetMapping("/internal/v1/harness/principal") Map<String, Object> principal(); }
    @FeignClient(name = "mi-probe", url = "${spring.cloud.openfeign.client.config.market-intelligence.url}", configuration = MarketIntelligenceFeignConfiguration.class) interface MiClient { @GetMapping("/internal/v1/harness/principal") Map<String, Object> principal(); }
    @RestController @RequestMapping("/harness/core") static class CoreProbe { private final BrokerClient broker; private final MiClient mi; CoreProbe(BrokerClient b, MiClient m) { broker = b; mi = m; } @GetMapping("/probe") Map<String, Object> probe() { return Map.of("broker", broker.principal(), "mi", mi.principal()); } }
    @RestController @RequestMapping("/internal/v1/harness") static class BrokerProbe { @GetMapping("/principal") Map<String, Object> principal(Authentication a) { var p = (BrokerPrincipal) a.getPrincipal(); return Map.of("service", p.username(), "userId", String.valueOf(p.userId())); } }
    @RestController @RequestMapping("/internal/v1/harness") static class MiProbe { @GetMapping("/principal") Map<String, Object> principal(Authentication a) { var p = (MiServicePrincipal) a.getPrincipal(); return Map.of("service", p.serviceName(), "audience", p.audience(), "delegatedActor", String.valueOf(p.delegatedActor())); } }
}
