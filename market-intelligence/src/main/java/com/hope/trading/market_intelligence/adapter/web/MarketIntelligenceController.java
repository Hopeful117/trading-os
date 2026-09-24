package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.scan.ActiveScanApplicationService;
import com.hope.trading.market_intelligence.application.scan.CreateActiveScanCommand;
import com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService;
import com.hope.trading.market_intelligence.application.observation.TrendContextReadModel;
import com.hope.trading.market_intelligence.application.observation.TrendContextReadService;
import com.hope.trading.market_intelligence.application.scope.ActiveScanScopeResolutionService;
import com.hope.trading.market_intelligence.domain.ConsolidatedIntelligence;
import com.hope.trading.market_intelligence.domain.IntelligenceAnalysisRequest;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecution;
import com.hope.trading.market_intelligence.domain.execution.IdempotencyKey;
import com.hope.trading.market_intelligence.security.MiUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/intelligence")
public class MarketIntelligenceController {
    private final AnalysisExecutionService executions;
    private final ActiveScanScopeResolutionService activeScanScopeResolution;
    private final ActiveScanApplicationService scans;
    private final com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository matches;
    private final TrendContextReadService trendContextReads;

    public MarketIntelligenceController(
            AnalysisExecutionService executions,
            ActiveScanScopeResolutionService activeScanScopeResolution,
            ActiveScanApplicationService scans,
            com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository matches
    ) {
        this(executions, activeScanScopeResolution, scans, matches, null);
    }

    @Autowired
    public MarketIntelligenceController(
            AnalysisExecutionService executions,
            ActiveScanScopeResolutionService activeScanScopeResolution,
            ActiveScanApplicationService scans,
            com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository matches,
            TrendContextReadService trendContextReads
    ) {
        this.executions = executions;
        this.activeScanScopeResolution = activeScanScopeResolution;
        this.scans = scans;
        this.matches = matches;
        this.trendContextReads = trendContextReads;
    }

    @PostMapping("/analyses")
    public ResponseEntity<AnalysisExecutionResponse> analyze(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @Valid @RequestBody IntelligenceAnalysisRequestDto request
    ) {
        UUID executionId = UUID.randomUUID();
        IntelligenceAnalysisRequest command = new IntelligenceAnalysisRequest(
                executionId,
                request.marketId(),
                request.mode(),
                request.objective()
        );
        AnalysisExecution execution = executions.create(
                command,
                new IdempotencyKey(idempotencyKey),
                requestId == null ? executionId.toString() : requestId,
                traceId == null ? executionId.toString() : traceId
        );
        return ResponseEntity.accepted()
                // Location of the Trading Core public trade-plan creation
                // entry point for this analysis execution (STORY-0019).
                .location(URI.create(
                        "/api/v1/trade-plans/analyses/" + execution.executionId()
                                + "/trade-plans"
                ))
                .body(AnalysisExecutionResponse.from(execution));
    }

    @PostMapping("/scans/scope")
    public ResponseEntity<ActiveScanScopeResolutionResponse> resolveScope(
            @Valid @RequestBody ActiveScanScopeResolutionRequestDto request
    ) {
        return ResponseEntity.ok(
                ActiveScanScopeResolutionResponse.from(
                        activeScanScopeResolution.resolve(
                                new com.hope.trading.market_intelligence.domain.scope.ActiveScanScopeResolutionRequest(
                                        request.accountId(), request.objective(), request.requestedMarketIds()
                                )
                        )
                )
        );
    }

    @GetMapping("/decision-context/{accountId}")
    public ResponseEntity<DecisionContextResponse> resolveDecisionContext(
            @PathVariable UUID accountId
    ) {
        return ResponseEntity.ok(
                DecisionContextResponse.from(activeScanScopeResolution.resolveDecisionContext(accountId))
        );
    }

    @PostMapping("/scans")
    public ResponseEntity<ActiveScanResponse> createScan(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication,
            @Valid @RequestBody CreateActiveScanRequestDto request
    ) {
        UUID actorId = actorId(authentication);
        ActiveScanResponse scan = ActiveScanResponse.from(
                scans.findOwnedProjection(
                        actorId,
                        scans.create(new CreateActiveScanCommand(
                                actorId,
                                idempotencyKey,
                                request.accountId(),
                                request.objective(),
                                request.requestedMarketIds()
                        )).scanId()),
                matches);
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/intelligence/scans/" + scan.scanId()))
                .body(scan);
    }

    @GetMapping("/scans")
    public ResponseEntity<List<ActiveScanSummary>> findRecentScans(
            Authentication authentication,
            @RequestParam(defaultValue = "10") int limit
    ) {
        UUID actorId = actorId(authentication);
        if (limit < 1 || limit > 100) {
            throw new com.hope.trading.market_intelligence.application.scan.ActiveScanException(
                    "INVALID_LIMIT",
                    "Limit must be between 1 and 100",
                    400
            );
        }
        return ResponseEntity.ok(scans.findRecentSummary(actorId, limit));
    }

    @GetMapping("/scans/{scanId}")
    public ResponseEntity<ActiveScanResponse> findScan(
            Authentication authentication,
            @PathVariable UUID scanId
    ) {
        return ResponseEntity.ok(
                ActiveScanResponse.from(
                        scans.findOwnedProjection(actorId(authentication), scanId), matches)
        );
    }

    @GetMapping("/analyses/{executionId}")
    public ResponseEntity<AnalysisExecutionResponse> find(
            @PathVariable UUID executionId
    ) {
        return ResponseEntity.ok(
                AnalysisExecutionResponse.from(executions.find(executionId))
        );
    }

    @GetMapping("/trend-context/{marketId}")
    public ResponseEntity<TrendContextReadModel> trendContext(
            Authentication authentication, @PathVariable UUID marketId) {
        actorId(authentication);
        return ResponseEntity.ok(trendContextReads.find(marketId));
    }

    @GetMapping("/analyses/{executionId}/result")
    public ResponseEntity<ConsolidatedIntelligence> result(
            @PathVariable UUID executionId
    ) {
        AnalysisExecution execution = executions.find(executionId);
        return execution.result()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.accepted().build());
    }

    @PostMapping("/analyses/{executionId}/cancel")
    public ResponseEntity<AnalysisExecutionResponse> cancel(
            @PathVariable UUID executionId
    ) {
        return ResponseEntity.ok(
                AnalysisExecutionResponse.from(executions.cancel(executionId))
        );
    }

    private UUID actorId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof MiUserPrincipal principal)) {
            throw new com.hope.trading.market_intelligence.application.scan.ActiveScanException(
                    "AUTHENTICATION_REQUIRED",
                    "Authenticated actor context is required",
                    401
            );
        }
        return principal.userId();
    }
}
