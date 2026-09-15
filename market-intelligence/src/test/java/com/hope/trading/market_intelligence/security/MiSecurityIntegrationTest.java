package com.hope.trading.market_intelligence.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.web.context.WebApplicationContext;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class MiSecurityIntegrationTest {
    private static final String SECRET =
            "YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFh";
    private static final String SERVICE_SECRET =
            "YmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJi";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void missingJwtIsRejectedOnSharedOpportunityRead() throws Exception {
        mvc.perform(get("/api/v1/opportunities/active"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidJwtIsRejected() throws Exception {
        mvc.perform(get("/api/v1/opportunities/active")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredJwtIsRejected() throws Exception {
        mvc.perform(get("/api/v1/opportunities/active")
                        .header(HttpHeaders.AUTHORIZATION, expiredBearer(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUsersCanReadTheSameSharedOpportunityCollection() throws Exception {
        mvc.perform(get("/api/v1/opportunities/active")
                        .header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID())))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/opportunities/active")
                        .header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID())))
                .andExpect(status().isOk());
    }

    @Test
    void compatibilityHeaderCannotOverrideAuthenticatedUser() throws Exception {
        UUID authenticatedUser = UUID.randomUUID();
        mvc.perform(get("/api/v1/intelligence/scans")
                        .header(HttpHeaders.AUTHORIZATION, bearer(authenticatedUser))
                        .header("X-Actor-Id", UUID.randomUUID()))
                .andExpect(status().isOk());
    }

    @Test
    void ordinaryUserCannotAccessInternalServiceEndpoint() throws Exception {
        mvc.perform(get("/internal/trade-planning/metrics")
                        .header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void ordinaryUserCannotAccessLegacyTradePlanEndpoint() throws Exception {
        mvc.perform(get("/trade-plans/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void analysisExecutionSurfaceRemainsClosedUntilOwnershipExists() throws Exception {
        mvc.perform(get("/api/v1/intelligence/analyses/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void validCoreServiceTokenCanCrossTheInternalSecurityBoundary() throws Exception {
        mvc.perform(get("/internal/v1/not-a-public-route")
                        .header("X-Service-Authorization", serviceBearer(null)))
                .andExpect(status().isNotFound());
    }

    @Test
    void wrongAudienceServiceTokenIsRejected() throws Exception {
        mvc.perform(get("/internal/v1/not-a-public-route")
                        .header("X-Service-Authorization",
                                serviceBearer(null, "broker-service", "trading-core")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validButUnauthorizedServiceCallerIsRejected() throws Exception {
        mvc.perform(get("/internal/v1/not-a-public-route")
                        .header("X-Service-Authorization",
                                serviceBearer(null, "market-intelligence", "other-service")))
                .andExpect(status().isForbidden());
    }

    @Test
    void userJwtCannotAccessInternalServiceRoute() throws Exception {
        mvc.perform(get("/internal/v1/not-a-public-route")
                        .header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void serviceJwtCannotAccessUserRoute() throws Exception {
        mvc.perform(get("/api/v1/opportunities/active")
                        .header("X-Service-Authorization", serviceBearer(null)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void serviceJwtCannotAccessManagementRoute() throws Exception {
        mvc.perform(get("/internal/trade-planning/metrics")
                        .header("X-Service-Authorization", serviceBearer(null)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void riskHandoffAcceptsServiceAuthenticationWithoutFakeDelegatedActor() throws Exception {
        mvc.perform(get("/internal/v1/trade-plans/" + UUID.randomUUID()
                        + "/versions/1/risk-validation-snapshot")
                        .header("X-Service-Authorization", serviceBearer(null)))
                .andExpect(status().isNotFound());
    }

    @Test
    void delegatedActorMismatchIsRejectedBeforePlanningService() throws Exception {
        UUID signedActor = UUID.randomUUID();
        UUID bodyActor = UUID.randomUUID();
        String body = """
                {"actorId":"%s","accountId":"%s","context":{"id":"%s","version":1,
                "capturedAt":"2026-09-15T12:00:00Z","ownerId":"%s","tradingAccountId":"%s",
                "accountCurrency":"USD","riskBudget":{"amount":100,"currency":"USD",
                "sourceId":"%s","sourceVersion":1},"preferences":{"id":"%s","version":1,
                "entryType":"MARKET","stopStrategy":"PERCENT","stopDistancePercent":1,
                "targetStrategy":"RISK_MULTIPLE","targetRiskMultiple":2,"horizon":"INTRADAY",
                "validity":"PT1H"}}}
                """.formatted(bodyActor, UUID.randomUUID(), UUID.randomUUID(), bodyActor,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/internal/v1/intelligence/opportunities/" + UUID.randomUUID() + "/trade-plans")
                        .header("X-Service-Authorization", serviceBearer(signedActor))
                        .header("Idempotency-Key", "mismatch")
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
    }

    private static String bearer(UUID userId) {
        return bearer(userId, new Date(System.currentTimeMillis() + 3_600_000));
    }

    private static String expiredBearer(UUID userId) {
        return bearer(userId, new Date(System.currentTimeMillis() - 1_000));
    }

    private static String bearer(UUID userId, Date expiration) {
        return "Bearer " + Jwts.builder()
                .subject(userId.toString())
                .claim("username", "trader")
                .claim("email", "trader@example.com")
                .claim("role", "ROLE_USER")
                .issuedAt(new Date())
                .expiration(expiration)
                .issuer("trading-os-test")
                .signWith(signingKey())
                .compact();
    }

    private static String serviceBearer(UUID actorId) {
        return serviceBearer(actorId, "market-intelligence", "trading-core");
    }

    private static String serviceBearer(UUID actorId, String audience, String issuer) {
        var builder = Jwts.builder().subject(issuer).issuer(issuer)
                .audience().add(audience).and().claim("type", "service")
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 60_000))
                .id(UUID.randomUUID().toString());
        if (actorId != null) builder.claim("actor_id", actorId.toString());
        return "Bearer " + builder.signWith(serviceSigningKey()).compact();
    }

    private static SecretKey signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
    }

    private static SecretKey serviceSigningKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(SERVICE_SECRET));
    }
}
