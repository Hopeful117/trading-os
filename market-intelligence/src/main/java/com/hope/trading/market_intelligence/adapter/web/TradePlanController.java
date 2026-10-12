package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.tradeplan.*;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityId;
import com.hope.trading.market_intelligence.domain.tradeplan.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;
import com.hope.trading.market_intelligence.application.port.TradingOpportunityRepository;
import com.hope.trading.market_intelligence.adapter.marketdata.MarketDataClient;

@RestController
@RequestMapping("/trade-plans")
public final class TradePlanController {
    private final TradePlanApplicationService service;
    private final TradePlanReplanningService replanning;
    private final com.hope.trading.market_intelligence.application.port.TradePlanningContextRepository contexts;
    private final TradingOpportunityRepository opportunities;
    private final MarketDataClient marketData;

    @Autowired
    public TradePlanController(
            TradePlanApplicationService service, TradePlanReplanningService replanning,
            com.hope.trading.market_intelligence.application.port.TradePlanningContextRepository contexts,
            TradingOpportunityRepository opportunities, MarketDataClient marketData) {
        this.service = service; this.replanning = replanning; this.contexts = contexts;
        this.opportunities = opportunities; this.marketData = marketData;
    }

    public TradePlanController(
            TradePlanApplicationService service, TradePlanReplanningService replanning,
            com.hope.trading.market_intelligence.application.port.TradePlanningContextRepository contexts) {
        this(service, replanning, contexts, null, null);
    }

    private TradePlanResponse view(TradePlan plan) {
        var context = contexts.find(plan.planningContext().id(), plan.planningContext().version())
                .orElseThrow();
        return TradePlanResponse.from(plan, context);
    }

    @PostMapping
    public ResponseEntity<Object> create(@Valid @RequestBody CreateTradePlanRequest request) {
        var identity = opportunities == null || marketData == null
                ? new InstrumentIdentity(request.instrumentProvider(), request.instrumentProductType())
                : canonicalIdentity(request.opportunityIds());
        return response(service.create(new TradePlanningRequest(
                request.opportunityIds().stream().map(OpportunityId::new)
                        .collect(Collectors.toUnmodifiableSet()),
                request.planningContextId(), request.contextVersion(), request.actorId(),
                request.marketPrice(),
                 null, null, "", Map.of(), identity.provider(), identity.productType())),
                HttpStatus.CREATED);
    }

    private InstrumentIdentity canonicalIdentity(Set<UUID> opportunityIds) {
        var markets = opportunityIds.stream()
                .map(id -> opportunities.findLatest(new OpportunityId(id)).orElseThrow())
                .map(opportunity -> opportunity.marketId().orElseThrow())
                .map(marketData::findMarket)
                .toList();
        if (markets.isEmpty() || markets.stream().anyMatch(market -> market.provider() == null
                || market.productType() == null)
                || markets.stream().map(market -> market.provider() + ":" + market.productType())
                .distinct().count() != 1) {
            throw new IllegalArgumentException("Canonical market identity is unavailable or inconsistent");
        }
        var market = markets.getFirst();
        return new InstrumentIdentity(market.provider(), market.productType());
    }

    private record InstrumentIdentity(String provider, String productType) { }
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
    public ResponseEntity<Object> replan(
            @PathVariable UUID id, @Valid @RequestBody ReplanTradePlanRequest request) {
        return response(replanning.replan(
                new TradePlanId(id), request.actorId(), request.marketPrice(),
                request.reason()), HttpStatus.CREATED);
    }
    private ResponseEntity<Object> response(TradePlanningResult result, HttpStatus successStatus) {
        if (result instanceof TradePlanningResult.Success success) {
            return ResponseEntity.status(successStatus).body(view(success.plan()));
        }
        TradePlanningResult.Failure failure = (TradePlanningResult.Failure) result;
        return ResponseEntity.unprocessableEntity().body(new TradePlanningFailureResponse(
                failure.reason().name(), failure.explanation(), failure.conflicts()));
    }
}
