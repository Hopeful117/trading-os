package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.scan.ActiveScanApplicationService;
import com.hope.trading.market_intelligence.application.scan.ActiveScanException;
import com.hope.trading.market_intelligence.application.scan.ActiveScanMarketOutcome;
import com.hope.trading.market_intelligence.application.scan.ActiveScanResultProjection;
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
        ActiveScanResultProjection projection = new ActiveScanResultProjection(
                scanId,
                accountId,
                "scan",
                ActiveScanStatus.READY_TO_DISPATCH,
                List.of(marketId),
                List.of(marketId),
                List.of(marketId),
                Instant.parse("2026-08-20T12:00:00Z"),
                Instant.parse("2026-08-20T12:00:01Z"),
                Instant.parse("2026-08-20T12:00:01Z"),
                new ActiveScanResultProjection.ActiveScanProgress(1, 1, 0, 1, 0, 0, 0),
                List.of(new ActiveScanResultProjection.MarketResult(
                        UUID.randomUUID(),
                        0,
                        marketId,
                        true,
                        List.of(),
                        UUID.randomUUID(),
                        null,
                        null,
                        ActiveScanMarketOutcome.RUNNING,
                        null,
                        null
                ))
        );
        when(scans.create(any())).thenReturn(ActiveScan.readyToDispatch(
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
        ));
        when(scans.findOwnedProjection(actorId, scanId)).thenReturn(projection);

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
                .andExpect(jsonPath("$.status").value("READY_TO_DISPATCH"))
                .andExpect(jsonPath("$.progress.totalCandidates").value(1))
                .andExpect(jsonPath("$.markets[0].outcome").value("RUNNING"));

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
        when(scans.findOwnedProjection(actorId, scanId)).thenThrow(new ActiveScanException(
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

    @Test
    void getOwnedCompletedScanReturnsProgressAndNoOpportunityOutcome() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID scanId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID marketId = UUID.randomUUID();
        ActiveScanApplicationService scans = mock(ActiveScanApplicationService.class);
        when(scans.findOwnedProjection(actorId, scanId)).thenReturn(new ActiveScanResultProjection(
                scanId,
                accountId,
                "scan",
                ActiveScanStatus.COMPLETED,
                List.of(marketId),
                List.of(marketId),
                List.of(marketId),
                Instant.parse("2026-08-20T12:00:00Z"),
                Instant.parse("2026-08-20T12:00:01Z"),
                Instant.parse("2026-08-20T12:00:02Z"),
                new ActiveScanResultProjection.ActiveScanProgress(1, 1, 0, 0, 1, 0, 0),
                List.of(new ActiveScanResultProjection.MarketResult(
                        UUID.randomUUID(),
                        0,
                        marketId,
                        true,
                        List.of(),
                        UUID.randomUUID(),
                        com.hope.trading.market_intelligence.domain.execution.AnalysisExecutionStatus.COMPLETED,
                        com.hope.trading.market_intelligence.domain.execution.AnalysisResultQuality.COMPLETE,
                        ActiveScanMarketOutcome.COMPLETED_NO_OPPORTUNITY,
                        new ActiveScanResultProjection.Diagnostic("NO_SIGNAL", "No opportunity"),
                        null
                ))
        ));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new MarketIntelligenceController(
                        mock(com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService.class),
                        mock(com.hope.trading.market_intelligence.application.scope.ActiveScanScopeResolutionService.class),
                        scans))
                .setControllerAdvice(new MarketIntelligenceExceptionHandler())
                .build();

        mvc.perform(get("/api/v1/intelligence/scans/{scanId}", scanId)
                        .header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.progress.completed").value(1))
                .andExpect(jsonPath("$.markets[0].outcome").value("COMPLETED_NO_OPPORTUNITY"))
                .andExpect(jsonPath("$.markets[0].diagnostic.code").value("NO_SIGNAL"));
    }

    @Test
    void getUnknownOwnedScanReturnsNotFound() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID scanId = UUID.randomUUID();
        ActiveScanApplicationService scans = mock(ActiveScanApplicationService.class);
        when(scans.findOwnedProjection(actorId, scanId)).thenThrow(new ActiveScanException(
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
