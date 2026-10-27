package com.hope.trading.trading_core.opportunitydecision.api;

import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.opportunitydecision.application.OpportunityDecisionService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}/opportunity-decisions")
public class OpportunityDecisionController {
    private final OpportunityDecisionService decisions;

    public OpportunityDecisionController(OpportunityDecisionService decisions) {
        this.decisions = decisions;
    }

    @PostMapping
    public ResponseEntity<Response> noTrade(
            @PathVariable UUID accountId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @jakarta.validation.Valid @RequestBody Request request,
            Authentication authentication) {
        UUID actorId = ((UserDto) authentication.getPrincipal()).getUserId();
        var decision = decisions.noTrade(actorId, accountId, request.opportunityId(),
                request.opportunityVersion(), request.thesisId(), request.thesisVersion(),
                request.rationale(), idempotencyKey);
        return ResponseEntity.ok(Response.from(decision));
    }

    public record Request(@NotNull UUID opportunityId, @NotNull Long opportunityVersion,
                           @NotNull UUID thesisId, @NotNull Long thesisVersion,
                           @Size(max = 2000) String rationale) { }

    public record Response(UUID id, UUID actorId, UUID accountId, UUID opportunityId,
                           long opportunityVersion, UUID thesisId, long thesisVersion,
                           String decision, String rationale, java.time.Instant createdAt) {
        static Response from(com.hope.trading.trading_core.opportunitydecision.domain.OpportunityDecision value) {
            return new Response(value.id(), value.actorId(), value.accountId(), value.opportunityId(),
                    value.opportunityVersion(), value.thesisId(), value.thesisVersion(),
                    value.decision().name(), value.rationale(), value.createdAt());
        }
    }
}
