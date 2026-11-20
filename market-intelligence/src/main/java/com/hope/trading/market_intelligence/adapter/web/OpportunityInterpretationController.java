package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.ai.OpportunityInterpretationOrchestrationService;
import com.hope.trading.market_intelligence.security.MiUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/opportunities")
public final class OpportunityInterpretationController {
    private final OpportunityInterpretationOrchestrationService orchestration;

    public OpportunityInterpretationController(OpportunityInterpretationOrchestrationService orchestration) {
        this.orchestration = orchestration;
    }

    @PostMapping("/{opportunityId}/interpretations")
    public ResponseEntity<OpportunityInterpretationResponse> interpret(
            @PathVariable java.util.UUID opportunityId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            Authentication authentication,
            @Valid @RequestBody OpportunityInterpretationRequest request) {
        if (!opportunityId.equals(request.opportunityId())) {
            throw new IllegalArgumentException("Path and body opportunity IDs must match");
        }
        if (!(authentication.getPrincipal() instanceof MiUserPrincipal principal)) {
            throw new IllegalArgumentException("Authenticated user principal is required");
        }
        var result = orchestration.request(
                principal.userId(), request.opportunityId(), request.opportunityVersion(),
                idempotencyKey, requestId, traceId, request.evaluationCutoff());
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/intelligence/analyses/" + result.execution().executionId()))
                .body(OpportunityInterpretationResponse.from(result));
    }
}
