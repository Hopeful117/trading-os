package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.scope.ActiveScanScopeResolutionService;
import com.hope.trading.market_intelligence.domain.scope.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActiveScanScopeResolutionControllerTest {
    @Test
    void exposesDeterministicScopeResolutionResponse() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID marketId = UUID.randomUUID();
        ActiveScanScopeResolutionService service = mock(ActiveScanScopeResolutionService.class);
        when(service.resolve(new ActiveScanScopeResolutionRequest(
                accountId, "scan", List.of(marketId))))
                .thenReturn(new ActiveScanScopeResolutionResult(
                        accountId,
                        "scan",
                        List.of(marketId),
                        List.of(marketId),
                        List.of(new MarketEligibilityDecision(
                                marketId, "BTC/USD", "KRAKEN", true, List.of()
                        )),
                        new EffectiveScanScope(List.of(marketId)),
                        Instant.parse("2026-08-20T10:15:30Z")
                ));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new MarketIntelligenceController(
                        mock(com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService.class),
                        service,
                        mock(com.hope.trading.market_intelligence.application.scan.ActiveScanApplicationService.class),
                        mock(com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository.class)))
                .setControllerAdvice(new MarketIntelligenceExceptionHandler())
                .build();

        mvc.perform(post("/api/v1/intelligence/scans/scope")
                        .contentType("application/json")
                        .content("""
                                {
                                  "accountId":"%s",
                                  "objective":"scan",
                                  "requestedMarketIds":["%s"]
                                }
                                """.formatted(accountId, marketId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.candidateMarketIds[0]").value(marketId.toString()))
                .andExpect(jsonPath("$.effectiveMarketIds[0]").value(marketId.toString()))
                .andExpect(jsonPath("$.decisions[0].eligible").value(true))
                .andExpect(jsonPath("$.decisions[0].reasons.length()").value(0));
    }
}
