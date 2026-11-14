package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.scan.ActiveScanApplicationService;
import com.hope.trading.market_intelligence.application.scan.ActiveScanException;
import com.hope.trading.market_intelligence.application.scan.ActiveScanResultProjection;
import com.hope.trading.market_intelligence.domain.scan.*;
import com.hope.trading.market_intelligence.security.MiUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PassiveCandidateScanControllerTest {
    private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");
    private final UUID actorId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID marketId = UUID.randomUUID();
    private final UUID opportunityId = UUID.randomUUID();
    private final ActiveScanApplicationService scans = mock(ActiveScanApplicationService.class);

    @Test
    void candidateFieldsReachTheCommandAndProvenanceIsProjected() throws Exception {
        UUID scanId = UUID.randomUUID();
        PassiveCandidateProvenance provenance = new PassiveCandidateProvenance(opportunityId, 4, UUID.randomUUID(),
                List.of(UUID.randomUUID()), UUID.randomUUID(), 1, UUID.randomUUID(), marketId, UUID.randomUUID(),
                marketId, "KRAKEN", "SPOT", NOW, NOW, NOW.plusSeconds(60), NOW,
                List.of(new PassiveEvidenceAssessment(PassiveEvidenceAssessment.Dimension.FRESHNESS,
                        PassiveEvidenceAssessment.Status.INCOMPATIBLE_REACQUIRED, "OLDER_THAN_PT30S")));
        when(scans.create(any())).thenReturn(ActiveScan.completedNoWork(scanId, actorId, accountId, "investigate",
                "key", "fingerprint", new ActiveScanScopeSnapshot(List.of(marketId), List.of(marketId), List.of(),
                        List.of(), NOW), NOW, provenance));
        when(scans.findOwnedProjection(actorId, scanId)).thenReturn(new ActiveScanResultProjection(
                scanId, accountId, "investigate", ActiveScanStatus.COMPLETED_NO_WORK, List.of(marketId),
                List.of(marketId), List.of(), NOW, NOW, NOW,
                new ActiveScanResultProjection.ActiveScanProgress(1, 0, 1, 0, 0, 0, 0), List.of(), provenance));

        mvc().perform(request(4))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.sourcePassiveCandidate.opportunityId").value(opportunityId.toString()))
                .andExpect(jsonPath("$.sourcePassiveCandidate.opportunityVersion").value(4))
                .andExpect(jsonPath("$.sourcePassiveCandidate.provider").value("KRAKEN"))
                .andExpect(jsonPath("$.sourcePassiveCandidate.evidenceAssessment[0].status")
                        .value("INCOMPATIBLE_REACQUIRED"));

        verify(scans).create(argThat(command -> command.passiveCandidate()
                .map(reference -> reference.opportunityId().equals(opportunityId)
                        && reference.opportunityVersion() == 4)
                .orElse(false)));
    }

    @Test
    void candidateErrorsUseExistingErrorBody() throws Exception {
        when(scans.create(any())).thenThrow(new ActiveScanException(
                "PASSIVE_CANDIDATE_NOT_CURRENT", "Passive candidate is no longer current: THESIS_NOT_CANDIDATE", 409));

        mvc().perform(request(1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PASSIVE_CANDIDATE_NOT_CURRENT"));
    }

    @Test
    void incompleteReferenceReturnsBadRequest() throws Exception {
        mvc().perform(post("/api/v1/intelligence/scans")
                        .header("Idempotency-Key", "key")
                        .with(authenticated())
                        .contentType("application/json")
                        .content("""
                                {"accountId":"%s","objective":"investigate","scopeMode":"SELECTED",
                                 "requestedMarketIds":["%s"],"sourcePassiveOpportunityId":"%s"}
                                """.formatted(accountId, marketId, opportunityId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSIVE_CANDIDATE_REFERENCE_INCOMPLETE"));
        verify(scans, never()).create(any());
    }

    private org.springframework.test.web.servlet.RequestBuilder request(long version) {
        return post("/api/v1/intelligence/scans")
                .header("Idempotency-Key", "key")
                .with(authenticated())
                .contentType("application/json")
                .content("""
                        {"accountId":"%s","objective":"investigate","scopeMode":"SELECTED",
                         "requestedMarketIds":["%s"],"sourcePassiveOpportunityId":"%s",
                         "sourcePassiveOpportunityVersion":%d}
                        """.formatted(accountId, marketId, opportunityId, version));
    }

    private RequestPostProcessor authenticated() {
        return request -> {
            request.setUserPrincipal(new UsernamePasswordAuthenticationToken(
                    new MiUserPrincipal(actorId, "trader", "trader@example.com"), null,
                    AuthorityUtils.NO_AUTHORITIES));
            return request;
        };
    }

    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new MarketIntelligenceController(
                        mock(com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService.class),
                        mock(com.hope.trading.market_intelligence.application.scope.ActiveScanScopeResolutionService.class),
                        scans,
                        mock(com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository.class)))
                .setControllerAdvice(new MarketIntelligenceExceptionHandler())
                .build();
    }
}
