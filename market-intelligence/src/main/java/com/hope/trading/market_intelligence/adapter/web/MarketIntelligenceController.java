package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.scan.ActiveScanApplicationService;
import com.hope.trading.market_intelligence.application.scan.CreateActiveScanCommand;
import com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService;
import com.hope.trading.market_intelligence.application.research.ResearchApplicationService;
import com.hope.trading.market_intelligence.application.observation.TrendContextReadModel;
import com.hope.trading.market_intelligence.application.observation.TrendContextReadService;
import com.hope.trading.market_intelligence.application.scope.ActiveScanScopeResolutionService;
import com.hope.trading.market_intelligence.domain.ConsolidatedIntelligence;
import com.hope.trading.market_intelligence.domain.IntelligenceAnalysisRequest;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecution;
import com.hope.trading.market_intelligence.domain.execution.IdempotencyKey;
import com.hope.trading.market_intelligence.security.MiUserPrincipal;
import com.hope.trading.market_intelligence.security.MiAgentPrincipal;
import com.hope.trading.market_intelligence.adapter.tradingcore.ResearchAuthorizationClient;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import feign.FeignException;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import com.hope.trading.market_intelligence.domain.research.ResearchTemporalScope;
import com.hope.trading.market_intelligence.domain.research.ResearchObjectiveType;
import com.hope.trading.market_intelligence.domain.outcome.OutcomeHorizon;

@RestController
@RequestMapping("/api/v1/intelligence")
public class MarketIntelligenceController {
    private final AnalysisExecutionService executions;
    private final ActiveScanScopeResolutionService activeScanScopeResolution;
    private final ActiveScanApplicationService scans;
    private final com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository matches;
    private final TrendContextReadService trendContextReads;
    private final ResearchApplicationService research;
    private final ResearchAuthorizationClient researchAuthorization;

    public MarketIntelligenceController(
            AnalysisExecutionService executions,
            ActiveScanScopeResolutionService activeScanScopeResolution,
            ActiveScanApplicationService scans,
            com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository matches
    ) {
        this(executions, activeScanScopeResolution, scans, matches, null, null, null);
    }

    public MarketIntelligenceController(
            AnalysisExecutionService executions,
            ActiveScanScopeResolutionService activeScanScopeResolution,
            ActiveScanApplicationService scans,
            com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository matches,
            TrendContextReadService trendContextReads
    ) {
        this(executions, activeScanScopeResolution, scans, matches, trendContextReads, null, null);
    }

    @Autowired
    public MarketIntelligenceController(
            AnalysisExecutionService executions,
            ActiveScanScopeResolutionService activeScanScopeResolution,
            ActiveScanApplicationService scans,
            com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository matches,
            TrendContextReadService trendContextReads,
            ResearchApplicationService research,
            ResearchAuthorizationClient researchAuthorization
    ) {
        this.executions = executions;
        this.activeScanScopeResolution = activeScanScopeResolution;
        this.scans = scans;
        this.matches = matches;
        this.trendContextReads = trendContextReads;
        this.research = research;
        this.researchAuthorization = researchAuthorization;
    }

    public MarketIntelligenceController(
            AnalysisExecutionService executions,
            ActiveScanScopeResolutionService activeScanScopeResolution,
            ActiveScanApplicationService scans,
            com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository matches,
            TrendContextReadService trendContextReads,
            ResearchApplicationService research) {
        this(executions, activeScanScopeResolution, scans, matches, trendContextReads, research, null);
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
                request.objective(),
                null, null, null, request.evaluationCutoff(), request.evidencePolicy()
        ).withTradeabilityEvidenceProfile(request.tradeabilityEvidenceProfile());
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

    @PostMapping("/research")
    public ResponseEntity<ResearchExecutionResponse> startResearch(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication,
            @Valid @RequestBody ResearchExecutionRequest request) {
        ResearchAuthorizationClient.AuthorizationResponse authorization = authorizeAgent(authentication, request.scopeId(), request.marketIds());
        ResearchExecutionResponse response = ResearchExecutionResponse.from(
                research.start(researchActorId(authentication), researchScope(authentication, request), idempotencyKey,
                        provenance(authentication, authorization),
                        new ResearchApplicationService.ResearchRequest(
                                request.marketIds(), request.objective(), request.depth(),
                                request.objectiveType(), temporalScope(request), request.provider(), request.productType())));
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/intelligence/research/" + response.researchId()))
                .body(response);
    }

    private ResearchTemporalScope temporalScope(ResearchExecutionRequest request) {
        if (request.timeframe() == null && request.horizon() == null
                && request.cutoffStart() == null && request.cutoffEnd() == null) {
            return null;
        }
        if (request.timeframe() == null || request.horizon() == null
                || request.cutoffStart() == null || request.cutoffEnd() == null) {
            throw new com.hope.trading.market_intelligence.application.research.ResearchException(
                    "INVALID_RESEARCH_SCOPE", "All temporal scope fields are required together", 400);
        }
        return new ResearchTemporalScope(request.timeframe().name(), new OutcomeHorizon(request.horizon()),
                request.cutoffStart(), request.cutoffEnd());
    }

    @GetMapping("/research/{researchId}")
    public ResponseEntity<ResearchExecutionResponse> findResearch(
            Authentication authentication, @PathVariable UUID researchId) {
        var execution = findOwnedResearch(authentication, researchId);
        authorizeAgent(authentication, execution.researchScopeId(), execution.marketIds());
        return ResponseEntity.ok(ResearchExecutionResponse.from(execution));
    }

    @GetMapping("/research/{researchId}/report")
    public ResponseEntity<?> findResearchReport(
            Authentication authentication, @PathVariable UUID researchId) {
        var execution = findOwnedResearch(authentication, researchId);
        authorizeAgent(authentication, execution.researchScopeId(), execution.marketIds());
        if (execution.report() == null) {
            return ResponseEntity.accepted().body(ResearchExecutionResponse.from(execution));
        }
        return ResponseEntity.ok(ResearchExecutionResponse.from(execution).report());
    }

    @PostMapping("/scans/scope")
    public ResponseEntity<ActiveScanScopeResolutionResponse> resolveScope(
            @Valid @RequestBody ActiveScanScopeResolutionRequestDto request
    ) {
        return ResponseEntity.ok(
                ActiveScanScopeResolutionResponse.from(
                        activeScanScopeResolution.resolve(scopeRequest(request))
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
        UUID actorId = scanActorId(authentication);
        authorizeScanAgent(authentication, request.accountId(), request.brokerAccountId(), request.requestedMarketIds(), idempotencyKey);
        ActiveScanResponse scan = ActiveScanResponse.from(
                scans.findOwnedProjection(
                        actorId,
                        scans.create(new CreateActiveScanCommand(
                                actorId,
                                idempotencyKey,
                                request.accountId(),
                                request.brokerAccountId(),
                                request.objective(),
                                request.requestedMarketIds(),
                                request.scopeMode(),
                                request.tradeabilityEvidenceProfile()
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
        UUID actorId = scanActorId(authentication);
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
                        scans.findOwnedProjection(scanActorId(authentication), scanId), matches)
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

    private UUID scanActorId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof MiAgentPrincipal agent) {
            return agent.agentId();
        }
        return actorId(authentication);
    }

    private void authorizeScanAgent(Authentication authentication, UUID accountId, UUID brokerAccountId,
                                    List<UUID> marketIds, String correlationId) {
        if (!(authentication != null && authentication.getPrincipal() instanceof MiAgentPrincipal agent)) {
            return;
        }
        if (brokerAccountId == null) {
            throw new com.hope.trading.market_intelligence.application.scan.ActiveScanException(
                    "SCAN_NOT_AUTHORIZED", "brokerAccountId is required for agent scans", 403);
        }
        ResearchAuthorizationClient.AuthorizationResponse response;
        try {
            response = researchAuthorization.authorize(new ResearchAuthorizationClient.AuthorizationRequest(
                    agent.agentId(), agent.credentialId(), agent.credentialVersion(), null, brokerAccountId,
                    marketIds == null ? Set.of() : Set.copyOf(marketIds), correlationId,
                    "scans:run", null, null, null, 0, 0, 0, 0));
        } catch (FeignException.Unauthorized exception) {
            throw new com.hope.trading.market_intelligence.application.scan.ActiveScanException(
                    "AUTHORIZATION_UNAVAILABLE", "Scan authorization is unavailable", 401);
        } catch (FeignException exception) {
            throw new com.hope.trading.market_intelligence.application.scan.ActiveScanException(
                    "SCAN_NOT_AUTHORIZED", "Scan is not authorized", 403);
        }
        if (response == null || !response.allowed()) {
            throw new com.hope.trading.market_intelligence.application.scan.ActiveScanException(
                    "SCAN_NOT_AUTHORIZED", "Scan is not authorized", 403);
        }
    }

    private UUID researchActorId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof MiAgentPrincipal agent) {
            return agent.agentId();
        }
        return actorId(authentication);
    }

    private com.hope.trading.market_intelligence.domain.research.ResearchExecution findOwnedResearch(
            Authentication authentication, UUID researchId) {
        if (authentication != null && authentication.getPrincipal() instanceof MiAgentPrincipal agent) {
            return research.findOwned(agent.agentId(), researchId);
        }
        return research.findOwnedByOwner(actorId(authentication), researchId);
    }

    private UUID researchScope(Authentication authentication, ResearchExecutionRequest request) {
        if (authentication != null && authentication.getPrincipal() instanceof MiAgentPrincipal) {
            if (request.scopeId() == null) {
                throw new com.hope.trading.market_intelligence.application.scan.ActiveScanException(
                        "RESEARCH_SCOPE_REQUIRED", "An explicit research scope is required", 400);
            }
            return request.scopeId();
        }
        return null;
    }

    private ResearchAuthorizationClient.AuthorizationResponse authorizeAgent(Authentication authentication, UUID scopeId, List<UUID> marketIds) {
        if (authentication != null && authentication.getPrincipal() instanceof MiAgentPrincipal agent) {
            ResearchAuthorizationClient.AuthorizationResponse response = callAuthorization(
                    new ResearchAuthorizationClient.AuthorizationRequest(agent.agentId(), agent.credentialId(),
                            agent.credentialVersion(), scopeId, Set.copyOf(marketIds), null));
            if (response == null || !response.allowed()) {
                throw new com.hope.trading.market_intelligence.application.scan.ActiveScanException(
                        "RESEARCH_NOT_AUTHORIZED", "Research scope is not authorized", 403);
            }
            return response;
        }
        return null;
    }

    private ResearchAuthorizationClient.AuthorizationResponse callAuthorization(
            ResearchAuthorizationClient.AuthorizationRequest request) {
        try {
            return researchAuthorization.authorize(request);
        } catch (FeignException.Unauthorized exception) {
            throw new com.hope.trading.market_intelligence.application.scan.ActiveScanException(
                    "AUTHORIZATION_UNAVAILABLE", "Research authorization is unavailable", 401);
        } catch (FeignException.Forbidden exception) {
            throw new com.hope.trading.market_intelligence.application.scan.ActiveScanException(
                    "RESEARCH_NOT_AUTHORIZED", "Research scope is not authorized", 403);
        } catch (FeignException exception) {
            throw new com.hope.trading.market_intelligence.application.scan.ActiveScanException(
                    "AUTHORIZATION_UNAVAILABLE", "Research authorization is unavailable", 503);
        }
    }

    private ResearchApplicationService.ResearchProvenance provenance(
            Authentication authentication, ResearchAuthorizationClient.AuthorizationResponse authorization) {
        if (!(authentication != null && authentication.getPrincipal() instanceof MiAgentPrincipal agent)
                || authorization == null) {
            return null;
        }
        return new ResearchApplicationService.ResearchProvenance(
                authorization.ownerUserId(), agent.agentId(), agent.credentialId(),
                authorization.grantId(), authorization.grantVersion(), agent.credentialVersion());
    }

    private com.hope.trading.market_intelligence.domain.scope.ActiveScanScopeResolutionRequest scopeRequest(
            ActiveScanScopeResolutionRequestDto request) {
        if (request.scopeMode() == null) {
            return new com.hope.trading.market_intelligence.domain.scope.ActiveScanScopeResolutionRequest(
                    request.accountId(), request.brokerAccountId(), request.objective(), request.requestedMarketIds(), null);
        }
        return new com.hope.trading.market_intelligence.domain.scope.ActiveScanScopeResolutionRequest(
                request.accountId(), request.brokerAccountId(), request.objective(), request.requestedMarketIds(), request.scopeMode());
    }
}
