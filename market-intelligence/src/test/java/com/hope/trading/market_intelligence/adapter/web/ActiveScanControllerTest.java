package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.scan.ActiveScanApplicationService;
import com.hope.trading.market_intelligence.application.scan.ActiveScanException;
import com.hope.trading.market_intelligence.application.scan.ActiveScanMarketOutcome;
import com.hope.trading.market_intelligence.application.scan.ActiveScanResultProjection;
import com.hope.trading.market_intelligence.domain.scan.*;
import com.hope.trading.market_intelligence.adapter.web.ActiveScanSummary;
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
                        scans,
                        mock(com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository.class)))
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
                        mock(ActiveScanApplicationService.class),
                        mock(com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository.class)))
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
                        scans,
                        mock(com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository.class)))
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
                        scans,
                        mock(com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository.class)))
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
                        scans,
                        mock(com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository.class)))
                .setControllerAdvice(new MarketIntelligenceExceptionHandler())
                .build();

        mvc.perform(get("/api/v1/intelligence/scans/{scanId}", scanId)
                        .header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACTIVE_SCAN_NOT_FOUND"));
    }

    @Test
    void findRecentScansReturnsEmptyListForNewActor() throws Exception {
        UUID actorId = UUID.randomUUID();
        ActiveScanApplicationService scans = mock(ActiveScanApplicationService.class);
        when(scans.findRecentSummary(actorId, 10)).thenReturn(List.of());

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new MarketIntelligenceController(
                        mock(com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService.class),
                        mock(com.hope.trading.market_intelligence.application.scope.ActiveScanScopeResolutionService.class),
                        scans,
                        mock(com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository.class)))
                .setControllerAdvice(new MarketIntelligenceExceptionHandler())
                .build();

        mvc.perform(get("/api/v1/intelligence/scans")
                        .header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void findRecentScansReturnsSummaries() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID scanId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        ActiveScanApplicationService scans = mock(ActiveScanApplicationService.class);
        when(scans.findRecentSummary(actorId, 10)).thenReturn(List.of(
                new ActiveScanSummary(scanId, accountId, ActiveScanStatus.COMPLETED, "test scan",
                        Instant.parse("2026-08-20T12:00:00Z"), Instant.parse("2026-08-20T12:05:00Z"))
        ));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new MarketIntelligenceController(
                        mock(com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService.class),
                        mock(com.hope.trading.market_intelligence.application.scope.ActiveScanScopeResolutionService.class),
                        scans,
                        mock(com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository.class)))
                .setControllerAdvice(new MarketIntelligenceExceptionHandler())
                .build();

        mvc.perform(get("/api/v1/intelligence/scans")
                        .header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].scanId").value(scanId.toString()))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$[0].objective").value("test scan"));
    }

    @Test
    void findRecentScansRespectsLimitParameter() throws Exception {
        UUID actorId = UUID.randomUUID();
        ActiveScanApplicationService scans = mock(ActiveScanApplicationService.class);
        when(scans.findRecentSummary(actorId, 3)).thenReturn(List.of());

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new MarketIntelligenceController(
                        mock(com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService.class),
                        mock(com.hope.trading.market_intelligence.application.scope.ActiveScanScopeResolutionService.class),
                        scans,
                        mock(com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository.class)))
                .setControllerAdvice(new MarketIntelligenceExceptionHandler())
                .build();

        mvc.perform(get("/api/v1/intelligence/scans")
                        .header("X-Actor-Id", actorId.toString())
                        .param("limit", "3"))
                .andExpect(status().isOk());

        verify(scans).findRecentSummary(actorId, 3);
    }

    @Test
    void findRecentScansRejectsLimitBelowMinimum() throws Exception {
        UUID actorId = UUID.randomUUID();
        ActiveScanApplicationService scans = mock(ActiveScanApplicationService.class);

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new MarketIntelligenceController(
                        mock(com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService.class),
                        mock(com.hope.trading.market_intelligence.application.scope.ActiveScanScopeResolutionService.class),
                        scans,
                        mock(com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository.class)))
                .setControllerAdvice(new MarketIntelligenceExceptionHandler())
                .build();

        mvc.perform(get("/api/v1/intelligence/scans")
                        .header("X-Actor-Id", actorId.toString())
                        .param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LIMIT"));
    }

    @Test
    void findRecentScansRejectsLimitAboveMaximum() throws Exception {
        UUID actorId = UUID.randomUUID();
        ActiveScanApplicationService scans = mock(ActiveScanApplicationService.class);

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new MarketIntelligenceController(
                        mock(com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService.class),
                        mock(com.hope.trading.market_intelligence.application.scope.ActiveScanScopeResolutionService.class),
                        scans,
                        mock(com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository.class)))
                .setControllerAdvice(new MarketIntelligenceExceptionHandler())
                .build();

        mvc.perform(get("/api/v1/intelligence/scans")
                        .header("X-Actor-Id", actorId.toString())
                        .param("limit", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LIMIT"));
    }

    @Test
    void findRecentScansRequiresActorContext() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new MarketIntelligenceController(
                        mock(com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService.class),
                        mock(com.hope.trading.market_intelligence.application.scope.ActiveScanScopeResolutionService.class),
                        mock(ActiveScanApplicationService.class),
                        mock(com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository.class)))
                .setControllerAdvice(new MarketIntelligenceExceptionHandler())
                .build();

        mvc.perform(get("/api/v1/intelligence/scans"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void findRecentScansDoesNotLeakAcrossActors() throws Exception {
        UUID actorA = UUID.randomUUID();
        UUID actorB = UUID.randomUUID();
        UUID scanA = UUID.randomUUID();
        ActiveScanApplicationService scans = mock(ActiveScanApplicationService.class);
        when(scans.findRecentSummary(actorA, 10)).thenReturn(List.of(
                new ActiveScanSummary(scanA, UUID.randomUUID(), ActiveScanStatus.COMPLETED, "a scan",
                        Instant.parse("2026-08-20T12:00:00Z"), Instant.parse("2026-08-20T12:00:00Z"))
        ));
        when(scans.findRecentSummary(actorB, 10)).thenReturn(List.of());

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new MarketIntelligenceController(
                        mock(com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService.class),
                        mock(com.hope.trading.market_intelligence.application.scope.ActiveScanScopeResolutionService.class),
                        scans,
                        mock(com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository.class)))
                .setControllerAdvice(new MarketIntelligenceExceptionHandler())
                .build();

        mvc.perform(get("/api/v1/intelligence/scans")
                        .header("X-Actor-Id", actorA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isNotEmpty());

        mvc.perform(get("/api/v1/intelligence/scans")
                        .header("X-Actor-Id", actorB.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }
}
