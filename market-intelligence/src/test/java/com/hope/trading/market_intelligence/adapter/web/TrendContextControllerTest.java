package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService;
import com.hope.trading.market_intelligence.application.observation.*;
import com.hope.trading.market_intelligence.application.scan.ActiveScanApplicationService;
import com.hope.trading.market_intelligence.application.scope.ActiveScanScopeResolutionService;
import com.hope.trading.market_intelligence.domain.observation.ObservationStatus;
import com.hope.trading.market_intelligence.domain.trendcontext.TrendContextTestFixtures;
import com.hope.trading.market_intelligence.security.MiUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TrendContextControllerTest {
    @Test
    void readContractRequiresAuthentication() throws Exception {
        MockMvc mvc = mvc(mock(TrendContextReadService.class));

        mvc.perform(get("/api/v1/intelligence/trend-context/{marketId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void authenticatedReadReturnsTypedApplicationModel() throws Exception {
        UUID marketId = TrendContextTestFixtures.MARKET_ID;
        TrendContextReadService reads = mock(TrendContextReadService.class);
        when(reads.find(marketId)).thenReturn(new TrendContextReadModel(
                marketId, "AVAILABLE", true, "VALID", UUID.randomUUID(),
                UUID.randomUUID(), 1L, ObservationStatus.ACTIVE,
                TrendContextTestFixtures.ASSESSMENT_AT,
                TrendContextTestFixtures.ASSESSMENT_AT.plusSeconds(3600), null, null));
        MockMvc mvc = mvc(reads);

        mvc.perform(get("/api/v1/intelligence/trend-context/{marketId}", marketId)
                        .with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationalStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.assessmentPresent").value(true))
                .andExpect(jsonPath("$.assessmentValidity").value("VALID"))
                .andExpect(jsonPath("$.assessment").doesNotExist());
        verify(reads).find(marketId);
    }

    private MockMvc mvc(TrendContextReadService reads) {
        return MockMvcBuilders.standaloneSetup(new MarketIntelligenceController(
                        mock(AnalysisExecutionService.class),
                        mock(ActiveScanScopeResolutionService.class),
                        mock(ActiveScanApplicationService.class),
                        mock(com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository.class),
                        reads))
                .setControllerAdvice(new MarketIntelligenceExceptionHandler())
                .build();
    }

    private RequestPostProcessor authenticated() {
        return request -> {
            request.setUserPrincipal(new UsernamePasswordAuthenticationToken(
                    new MiUserPrincipal(UUID.randomUUID(), "trader", "trader@example.com"),
                    null, AuthorityUtils.NO_AUTHORITIES));
            return request;
        };
    }
}
