package com.hope.trading.trading_core.tradeplanning.approval;

import com.hope.trading.trading_core.security.AgentPrincipal;
import com.hope.trading.trading_core.dto.UserDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
public class TradePlanApprovalController {
    private final TradePlanApprovalService service;

    public TradePlanApprovalController(TradePlanApprovalService service) { this.service = service; }

    @PostMapping("/api/v1/agent/trade-plan-approvals")
    public ResponseEntity<TradePlanApprovalDtos.Response> request(@RequestBody TradePlanApprovalDtos.Request request,
            Authentication authentication, @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return ResponseEntity.ok(service.request(agent(authentication), request, idempotencyKey, correlationId));
    }

    @GetMapping("/api/v1/agent/trade-plan-approvals/{id}")
    public ResponseEntity<TradePlanApprovalDtos.Response> agentGet(@PathVariable UUID id, Authentication authentication,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return ResponseEntity.ok(service.agentGet(agent(authentication), id, correlationId));
    }

    @GetMapping("/api/v1/agent/trade-plans/{planId}/versions/{version}")
    public ResponseEntity<?> agentPlan(@PathVariable UUID planId, @PathVariable long version,
            @RequestParam UUID accountId, Authentication authentication,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return ResponseEntity.ok(service.agentPlan(agent(authentication), accountId, planId, version, correlationId));
    }

    @GetMapping("/api/v1/trade-plan-approvals/{id}")
    public ResponseEntity<TradePlanApprovalDtos.Response> humanGet(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(service.humanGet(user(authentication).getUserId(), id));
    }

    @PostMapping("/api/v1/trade-plan-approvals/{id}/approve")
    public ResponseEntity<TradePlanApprovalDtos.Response> approve(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(service.decide(user(authentication).getUserId(), id, true));
    }

    @PostMapping("/api/v1/trade-plan-approvals/{id}/reject")
    public ResponseEntity<TradePlanApprovalDtos.Response> reject(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(service.decide(user(authentication).getUserId(), id, false));
    }

    private AgentPrincipal agent(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AgentPrincipal value))
            throw new com.hope.trading.trading_core.agent.operation.application.AgentOperationException(403, "AGENT_AUTHENTICATION_REQUIRED");
        return value;
    }
    private UserDto user(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDto value))
            throw new com.hope.trading.trading_core.agent.operation.application.AgentOperationException(403, "HUMAN_AUTHENTICATION_REQUIRED");
        return value;
    }
}
