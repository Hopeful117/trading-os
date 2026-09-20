package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.scope.ActiveScanScopeResolutionService;
import com.hope.trading.market_intelligence.adapter.tradingcore.TradingCoreAccountClient;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Test
    void exposesAccountFirstDecisionContextWithEligibleAndExcludedMarkets() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID eligibleMarketId = UUID.randomUUID();
        UUID excludedMarketId = UUID.randomUUID();
        ActiveScanScopeResolutionService service = mock(ActiveScanScopeResolutionService.class);
        TradingCoreAccountClient.TradingCoreAccountResponse account =
                new TradingCoreAccountClient.TradingCoreAccountResponse(
                        accountId,
                        UUID.randomUUID(),
                        "Paper account",
                        "USD",
                        null,
                        null,
                        null,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "1.0.0",
                        UUID.randomUUID(),
                        1L
                );
        when(service.resolveDecisionContext(accountId)).thenReturn(
                new com.hope.trading.market_intelligence.application.scope.DecisionContextResolution(
                        account,
                        new ActiveScanScopeResolutionResult(
                                accountId,
                                "",
                                List.of(),
                                List.of(eligibleMarketId, excludedMarketId),
                                List.of(
                                        new MarketEligibilityDecision(
                                                eligibleMarketId, "BTC/USD", "KRAKEN", true, List.of()),
                                        new MarketEligibilityDecision(
                                                excludedMarketId,
                                                "ETH/USD",
                                                "KRAKEN",
                                                false,
                                                List.of(MarketEligibilityReason.MARKET_NOT_TRADABLE))
                                ),
                                new EffectiveScanScope(List.of(eligibleMarketId)),
                                Instant.parse("2026-08-20T10:15:30Z")
                        )
                )
        );

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new MarketIntelligenceController(
                        mock(com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService.class),
                        service,
                        mock(com.hope.trading.market_intelligence.application.scan.ActiveScanApplicationService.class),
                        mock(com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository.class)))
                .setControllerAdvice(new MarketIntelligenceExceptionHandler())
                .build();

        mvc.perform(get("/api/v1/intelligence/decision-context/{accountId}", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.account.riskProfileSemanticVersion").value("1.0.0"))
                .andExpect(jsonPath("$.eligibleMarketIds[0]").value(eligibleMarketId.toString()))
                .andExpect(jsonPath("$.markets[0].eligible").value(true))
                .andExpect(jsonPath("$.markets[1].eligible").value(false))
                .andExpect(jsonPath("$.markets[1].reasons[0]").value("MARKET_NOT_TRADABLE"));
    }
}
