package com.hope.trading.trading_core.tradeplanning.api;

import com.hope.trading.trading_core.tradeplanning.application.OpportunityTradePlanOrchestrationService;
import com.hope.trading.trading_core.tradeplanning.infrastructure.MarketIntelligenceTradePlanningClient.PlanTransport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

/**
 * Explicit trader decision + owner-scoped plan read (STORY-0023). The actor
 * always comes from the authenticated principal, never from the payload.
 */
@RestController
@RequestMapping("/api/v1/trade-plans")
public class TradePlanDecisionController {
    private static final Set<String> DECISIONS = Set.of("ACCEPT", "REJECT");

    private final OpportunityTradePlanOrchestrationService orchestration;

    public TradePlanDecisionController(OpportunityTradePlanOrchestrationService orchestration) {
        this.orchestration = orchestration;
    }

    @PostMapping("/{planId}/versions/{version}/decisions")
    public ResponseEntity<PlanTransport> decide(
            @PathVariable UUID planId, @PathVariable long version,
            @Valid @RequestBody DecisionRequest request,
            Authentication authentication) {
        if (!DECISIONS.contains(request.decision())) {
            throw new IllegalArgumentException("Unsupported decision");
        }
        var user = OpportunityTradePlanController.authenticated(authentication);
        return ResponseEntity.ok(orchestration.decide(
                user.getUserId(), planId, version, request.decision()));
    }

    @GetMapping("/{planId}/versions/{version}")
    public ResponseEntity<PlanTransport> load(
            @PathVariable UUID planId, @PathVariable long version,
            Authentication authentication) {
        var user = OpportunityTradePlanController.authenticated(authentication);
        return ResponseEntity.ok(orchestration.load(user.getUserId(), planId, version));
    }

    public record DecisionRequest(@NotBlank String decision) { }
}
