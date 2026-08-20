package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.scan.ActiveScanApplicationService;
import com.hope.trading.market_intelligence.application.scan.ActiveScanException;
import com.hope.trading.market_intelligence.domain.scan.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActiveScanControllerTest {
    @Test
    void createReturnsAcceptedLocationAndBody() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID scanId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID marketId = UUID.randomUUID();
        ActiveScanApplicationService scans = mock(ActiveScanApplicationService.class);
        ActiveScanApplicationService.ActiveScanView view = new ActiveScanApplicationService.ActiveScanView(
                ActiveScan.readyToDispatch(
                        scanId,
                        actorId,
                        accountId,
                        "scan",
                        "scan-key",
                        "fingerprint",
                        new ActiveScanScopeSnapshot(
                                List.of(marketId),
                                List.of(marketId),
                                List.of(new ActiveScanDecisionSnapshot(
                                        marketId, "ACH/EUR", "KRAKEN", true, List.of()
                                )),
                                List.of(marketId),
                                Instant.parse("2026-08-20T12:00:00Z")
                        ),
                        Instant.parse("2026-08-20T12:00:01Z")
                ),
                List.of(ActiveScanMarket.registered(
                        UUID.randomUUID(), scanId, 0, marketId, UUID.randomUUID(),
                        Instant.parse("2026-08-20T12:00:01Z")
                ))
        );
        when(scans.create(any())).thenReturn(view.scan());
        when(scans.findOwned(actorId, scanId)).thenReturn(view);

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new MarketIntelligenceController(
                        mock(com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService.class),
                        mock(com.hope.trading.market_intelligence.application.scope.ActiveScanScopeResolutionService.class),
                        scans))
                .setControllerAdvice(new MarketIntelligenceExceptionHandler())
                .build();

        mvc.perform(post("/api/v1/intelligence/scans")
                        .header("Idempotency-Key", "scan-key")
                        .header("X-Actor-Id", actorId.toString())
                        .contentType("application/json")
                        .content("""
                                {
                                  "accountId":"%s",
                                  "objective":"scan",
                                  "requestedMarketIds":["%s"]
                                }
                                """.formatted(accountId, marketId)))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/intelligence/scans/" + scanId))
                .andExpect(jsonPath("$.scanId").value(scanId.toString()))
                .andExpect(jsonPath("$.status").value("READY_TO_DISPATCH"));

        verify(scans).create(any());
    }

    @Test
    void getOwnedScanRequiresActorContext() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new MarketIntelligenceController(
                        mock(com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService.class),
                        mock(com.hope.trading.market_intelligence.application.scope.ActiveScanScopeResolutionService.class),
                        mock(ActiveScanApplicationService.class)))
                .setControllerAdvice(new MarketIntelligenceExceptionHandler())
                .build();

        mvc.perform(get("/api/v1/intelligence/scans/{scanId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void getOwnedScanDoesNotExposeAnotherActorScan() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID scanId = UUID.randomUUID();
        ActiveScanApplicationService scans = mock(ActiveScanApplicationService.class);
        when(scans.findOwned(actorId, scanId)).thenThrow(new ActiveScanException(
                "ACTIVE_SCAN_NOT_FOUND",
                "Active scan not found: " + scanId,
                404
        ));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new MarketIntelligenceController(
                        mock(com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService.class),
                        mock(com.hope.trading.market_intelligence.application.scope.ActiveScanScopeResolutionService.class),
                        scans))
                .setControllerAdvice(new MarketIntelligenceExceptionHandler())
                .build();

        mvc.perform(get("/api/v1/intelligence/scans/{scanId}", scanId)
                        .header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACTIVE_SCAN_NOT_FOUND"));
    }
}
