package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.tradeplan.*;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityId;
import com.hope.trading.market_intelligence.domain.tradeplan.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/trade-plans")
public final class TradePlanController {
    private final TradePlanApplicationService service;
    private final TradePlanReplanningService replanning;
    private final com.hope.trading.market_intelligence.application.port.TradePlanningContextRepository contexts;

    public TradePlanController(
            TradePlanApplicationService service, TradePlanReplanningService replanning,
            com.hope.trading.market_intelligence.application.port.TradePlanningContextRepository contexts) {
        this.service = service; this.replanning = replanning; this.contexts = contexts;
    }

    private TradePlanResponse view(TradePlan plan) {
        var context = contexts.find(plan.planningContext().id(), plan.planningContext().version())
                .orElseThrow();
        return TradePlanResponse.from(plan, context);
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateTradePlanRequest request) {
        return response(service.create(new TradePlanningRequest(
                request.opportunityIds().stream().map(OpportunityId::new)
                        .collect(Collectors.toUnmodifiableSet()),
                request.planningContextId(), request.contextVersion(), request.actorId(),
                request.marketPrice(),
                null, null, "")), HttpStatus.CREATED);
    }
    @GetMapping("/{id}")
    public ResponseEntity<TradePlanResponse> latest(@PathVariable UUID id) {
        return service.latest(new TradePlanId(id)).map(this::view)
                .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
    @GetMapping("/{id}/versions")
    public ResponseEntity<List<TradePlanResponse>> versions(@PathVariable UUID id) {
        List<TradePlanResponse> history = service.history(new TradePlanId(id)).stream()
                .map(this::view).toList();
        return history.isEmpty() ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(history);
    }
    @PostMapping("/{id}/replan")
    public ResponseEntity<?> replan(
            @PathVariable UUID id, @Valid @RequestBody ReplanTradePlanRequest request) {
        return response(replanning.replan(
                new TradePlanId(id), request.actorId(), request.marketPrice(),
                request.reason()), HttpStatus.CREATED);
    }
    private ResponseEntity<?> response(TradePlanningResult result, HttpStatus successStatus) {
        if (result instanceof TradePlanningResult.Success success) {
            return ResponseEntity.status(successStatus).body(view(success.plan()));
        }
        TradePlanningResult.Failure failure = (TradePlanningResult.Failure) result;
        return ResponseEntity.unprocessableEntity().body(new TradePlanningFailureResponse(
                failure.reason().name(), failure.explanation(), failure.conflicts()));
    }
}
