package com.hope.trading.market_data.security;

import com.hope.trading.market_data.service.MarketPriceSnapshotService;
import com.hope.trading.market_data.service.MarketFactsService;
import com.hope.trading.market_data.service.MarketSynchronization;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarketDataSecurityIntegrationTest {
    private static final String SECRET =
            "YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFh";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MarketPriceSnapshotService snapshotService;

    @MockitoBean
    private MarketFactsService marketFactsService;

    @MockitoBean
    private MarketSynchronization marketSynchronization;

    @Test
    void internalSnapshotRequiresServiceCredential() throws Exception {
        mockMvc.perform(post("/internal/markets/prices/snapshot")
                        .contentType("application/json")
                        .content("{\"marketIds\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTradingCoreCredentialCanUseInternalSnapshot() throws Exception {
        mockMvc.perform(post("/internal/markets/prices/snapshot")
                        .header("X-Service-Authorization", "Bearer " + serviceToken())
                        .contentType("application/json")
                        .content("{\"marketIds\":[]}"))
                .andExpect(status().isOk());
    }

    @Test
    void credentialSignedForAnotherServiceIsRejected() throws Exception {
        mockMvc.perform(post("/internal/markets/prices/snapshot")
                        .header("X-Service-Authorization", "Bearer " + serviceToken("other-service"))
                        .contentType("application/json")
                        .content("{\"marketIds\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void marketIntelligenceCredentialCanUseOnlyMarketFactsEndpoint() throws Exception {
        mockMvc.perform(get("/internal/v1/market-facts/00000000-0000-0000-0000-000000000001")
                        .header("X-Service-Authorization", "Bearer " + serviceToken("market-intelligence"))
                        .param("interval", "ONE_MINUTE")
                        .param("activityWindowMinutes", "3")
                        .param("readinessLookbackCandles", "3")
                        .param("minimumCompletedCandles", "1")
                        .param("maxObservationAgeSeconds", "300"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/internal/markets/prices/snapshot")
                        .header("X-Service-Authorization", "Bearer " + serviceToken("market-intelligence"))
                        .contentType("application/json")
                        .content("{\"marketIds\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownServiceCannotUseMarketFactsEndpoint() throws Exception {
        mockMvc.perform(get("/internal/v1/market-facts/00000000-0000-0000-0000-000000000001")
                        .header("X-Service-Authorization", "Bearer " + serviceToken("other-service"))
                        .param("interval", "ONE_MINUTE")
                        .param("activityWindowMinutes", "3")
                        .param("readinessLookbackCandles", "3")
                        .param("minimumCompletedCandles", "1")
                        .param("maxObservationAgeSeconds", "300"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicCatalogueRemainsAvailableWithoutServiceCredential() throws Exception {
        mockMvc.perform(get("/api/v1/markets"))
                .andExpect(status().isOk());
    }

    @Test
    void marketSynchronizationRequiresServiceCredential() throws Exception {
        mockMvc.perform(post("/api/v1/markets/synchronize"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void subscriptionMutationRequiresServiceCredential() throws Exception {
        mockMvc.perform(post("/api/v1/markets/00000000-0000-0000-0000-000000000001/subscriptions")
                        .contentType("application/json")
                        .content("{\"type\":\"TICKER\",\"parameters\":{}}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void serviceCredentialCanUseMarketSynchronization() throws Exception {
        mockMvc.perform(post("/api/v1/markets/synchronize")
                        .header("X-Service-Authorization", "Bearer " + serviceToken()))
                .andExpect(status().isOk());
    }

    private String serviceToken() {
        return serviceToken("trading-core");
    }

    private String serviceToken(String issuer) {
        Instant now = Instant.now();
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET));
        return Jwts.builder()
                .issuer(issuer)
                .subject(issuer)
                .audience().add("market-data").and()
                .claim("type", "service")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(60)))
                .id(UUID.randomUUID().toString())
                .signWith(key)
                .compact();
    }
}
