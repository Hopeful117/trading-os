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
    public TradePlanController(
            TradePlanApplicationService service, TradePlanReplanningService replanning) {
        this.service = service; this.replanning = replanning;
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateTradePlanRequest request) {
        return response(service.create(new TradePlanningRequest(
                request.opportunityIds().stream().map(OpportunityId::new)
                        .collect(Collectors.toUnmodifiableSet()),
                request.tradingContextId(), request.contextVersion(), request.actorId(),
                request.marketPrice(), PlanningPreferences.conservative(),
                null, null, "")), HttpStatus.CREATED);
    }
    @GetMapping("/{id}")
    public ResponseEntity<TradePlanResponse> latest(@PathVariable UUID id) {
        return service.latest(new TradePlanId(id)).map(TradePlanResponse::from)
                .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
    @GetMapping("/{id}/versions")
    public ResponseEntity<List<TradePlanResponse>> versions(@PathVariable UUID id) {
        List<TradePlanResponse> history = service.history(new TradePlanId(id)).stream()
                .map(TradePlanResponse::from).toList();
        return history.isEmpty() ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(history);
    }
    @PostMapping("/{id}/replan")
    public ResponseEntity<?> replan(
            @PathVariable UUID id, @Valid @RequestBody ReplanTradePlanRequest request) {
        return response(replanning.replan(
                new TradePlanId(id), request.actorId(), request.marketPrice(),
                PlanningPreferences.conservative(), request.reason()), HttpStatus.CREATED);
    }
    private ResponseEntity<?> response(TradePlanningResult result, HttpStatus successStatus) {
        if (result instanceof TradePlanningResult.Success success) {
            return ResponseEntity.status(successStatus).body(TradePlanResponse.from(success.plan()));
        }
        TradePlanningResult.Failure failure = (TradePlanningResult.Failure) result;
        return ResponseEntity.unprocessableEntity().body(new TradePlanningFailureResponse(
                failure.reason().name(), failure.explanation(), failure.conflicts()));
    }
}
