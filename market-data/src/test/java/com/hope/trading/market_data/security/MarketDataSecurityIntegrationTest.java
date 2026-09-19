package com.hope.trading.market_data.security;

import com.hope.trading.market_data.service.MarketPriceSnapshotService;
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
    void publicCatalogueRemainsAvailableWithoutServiceCredential() throws Exception {
        mockMvc.perform(get("/api/v1/markets"))
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
