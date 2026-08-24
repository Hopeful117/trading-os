package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.pipeline.OpportunityTradePlanGenerationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/intelligence/opportunities")
public class InternalOpportunityTradePlanController {
    private final OpportunityTradePlanGenerationService service;

    public InternalOpportunityTradePlanController(OpportunityTradePlanGenerationService service) {
        this.service = service;
    }

    @PostMapping("/{opportunityId}/trade-plans")
    public ResponseEntity<OpportunityTradePlanGenerationService.GenerationResponse> generate(
            @PathVariable UUID opportunityId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody InternalOpportunityTradePlanRequest request) {
        return ResponseEntity.ok(service.generate(
                opportunityId, request.actorId(), request.accountId(),
                context(request)));
    }

    private com.hope.trading.market_intelligence.domain.tradeplan.TradePlanningContext context(
            InternalOpportunityTradePlanRequest request) {
        var value = request.context();
        var budget = value.riskBudget();
        var preferences = value.preferences();
        return new com.hope.trading.market_intelligence.domain.tradeplan.TradePlanningContext(
                value.id(), value.version(), value.capturedAt(), value.ownerId(),
                value.tradingAccountId(), value.accountCurrency(),
                new com.hope.trading.market_intelligence.domain.tradeplan.RiskBudget(
                        budget.amount(), budget.currency(), budget.sourceId(), budget.sourceVersion()),
                new com.hope.trading.market_intelligence.domain.tradeplan.PlanningPreferences(
                        preferences.id(), preferences.version(),
                        com.hope.trading.market_intelligence.domain.tradeplan.EntryType.valueOf(
                                preferences.entryType()),
                        com.hope.trading.market_intelligence.domain.tradeplan.PlanningPreferences.StopStrategy.valueOf(
                                preferences.stopStrategy()),
                        preferences.stopDistancePercent(),
                        com.hope.trading.market_intelligence.domain.tradeplan.PlanningPreferences.TargetStrategy.valueOf(
                                preferences.targetStrategy()),
                        preferences.targetRiskMultiple(),
                        com.hope.trading.market_intelligence.domain.tradeplan.PlanningPreferences.PlanningHorizon.valueOf(
                                preferences.horizon()),
                        preferences.validity()));
    }
}
