package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.port.TradePlanningContextRepository;
import com.hope.trading.market_intelligence.application.tradeplan.TradePlanDecisionService;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanVersion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/trade-plans/{planId}/versions/{version}")
public class InternalTradePlanDecisionController {
    private final TradePlanDecisionService service;
    private final TradePlanningContextRepository contexts;

    public InternalTradePlanDecisionController(
            TradePlanDecisionService service, TradePlanningContextRepository contexts) {
        this.service = service;
        this.contexts = contexts;
    }

    private TradePlanResponse view(com.hope.trading.market_intelligence.domain.tradeplan.TradePlan plan) {
        var context = contexts.find(
                        plan.planningContext().id(), plan.planningContext().version())
                .orElseThrow();
        return TradePlanResponse.from(plan, context);
    }

    @PostMapping("/decisions")
    public ResponseEntity<TradePlanResponse> decide(
            @PathVariable UUID planId, @PathVariable long version,
            @Valid @RequestBody DecisionRequest request) {
        var decided = service.decide(planId, version, request.actorId(),
                TradePlanDecisionService.Decision.valueOf(request.decision()));
        return ResponseEntity.ok(view(decided));
    }

    @GetMapping
    public ResponseEntity<TradePlanResponse> load(
            @PathVariable UUID planId, @PathVariable long version,
            @RequestParam UUID actorId) {
        return ResponseEntity.ok(view(service.loadForActor(planId, version, actorId)));
    }

    public record DecisionRequest(@NotNull UUID actorId, @NotBlank String decision) { }
}
