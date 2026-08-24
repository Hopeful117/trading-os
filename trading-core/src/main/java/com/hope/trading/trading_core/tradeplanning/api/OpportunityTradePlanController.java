package com.hope.trading.trading_core.tradeplanning.api;

import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.tradeplanning.application.AnalysisTradePlanGenerationException;
import com.hope.trading.trading_core.tradeplanning.application.OpportunityTradePlanOrchestrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trade-plans/opportunities")
public class OpportunityTradePlanController {
    private final OpportunityTradePlanOrchestrationService orchestration;

    public OpportunityTradePlanController(OpportunityTradePlanOrchestrationService orchestration) {
        this.orchestration = orchestration;
    }

    @PostMapping("/{opportunityId}/trade-plans")
    public ResponseEntity<Response> create(
            @PathVariable UUID opportunityId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody Request request,
            Authentication authentication) {
        UserDto user = authenticated(authentication);
        var response = orchestration.createFromOpportunity(
                user.getUserId(), opportunityId, request.accountId(), idempotencyKey);
        return ResponseEntity.ok(new Response(response.tradePlanId(), response.tradePlanVersion()));
    }

    public record Request(@NotNull UUID accountId) { }
    public record Response(UUID tradePlanId, long tradePlanVersion) { }

    static UserDto authenticated(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDto user)) {
            throw new AnalysisTradePlanGenerationException(
                    "AUTHENTICATION_REQUIRED", "Authenticated user is required", 401);
        }
        return user;
    }
}
