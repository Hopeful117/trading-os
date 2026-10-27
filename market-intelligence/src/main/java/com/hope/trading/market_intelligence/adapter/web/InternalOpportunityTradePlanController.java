package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.pipeline.OpportunityTradePlanGenerationService;
import com.hope.trading.market_intelligence.application.port.MarketThesisRepository;
import com.hope.trading.market_intelligence.application.port.TradingOpportunityRepository;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityId;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.Objects;

@RestController
@RequestMapping("/internal/v1/intelligence/opportunities")
public class InternalOpportunityTradePlanController {
    private final OpportunityTradePlanGenerationService service;
    private final TradingOpportunityRepository opportunities;
    private final MarketThesisRepository theses;

    public InternalOpportunityTradePlanController(OpportunityTradePlanGenerationService service,
                                                  TradingOpportunityRepository opportunities,
                                                  MarketThesisRepository theses) {
        this.service = service;
        this.opportunities = opportunities;
        this.theses = theses;
    }

    @GetMapping("/{opportunityId}/versions/{version}")
    public ResponseEntity<OpportunityAuthorityResponse> authority(
            @PathVariable UUID opportunityId, @PathVariable long version) {
        return opportunities.find(new OpportunityId(opportunityId),
                        new com.hope.trading.market_intelligence.domain.opportunity.OpportunityVersion(version))
                .flatMap(opportunity -> opportunity.marketThesis()
                        .flatMap(reference -> theses.findByIdAndVersion(
                                reference.thesisId(), reference.thesisVersion()))
                        .map(thesis -> new OpportunityAuthorityResponse(
                                opportunity.id().value(), opportunity.version().value(),
                                opportunity.accountId().orElse(null), opportunity.marketId().orElse(null),
                                opportunity.instrument(), opportunity.direction().name(),
                                opportunity.marketThesis().orElseThrow().thesisId(),
                                opportunity.marketThesis().orElseThrow().thesisVersion(),
                                thesis.state().name(), thesis.marketId(), thesis.instrument(),
                                thesis.validFrom(), thesis.validUntil().orElse(null),
                                thesis.sourceCutoff(), thesis.evaluatedAt())))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{opportunityId}/trade-plans")
    public ResponseEntity<OpportunityTradePlanGenerationService.GenerationResponse> generate(
            @PathVariable UUID opportunityId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody InternalOpportunityTradePlanRequest request,
            Authentication authentication) {
        UUID actorId = ((com.hope.trading.market_intelligence.security.MiServicePrincipal)
                Objects.requireNonNull(authentication.getPrincipal(), "principal is required"))
                .requireMatchingActor(request.actorId());
        return ResponseEntity.ok(service.generate(
                opportunityId, actorId, request.opportunityVersion(), request.accountId(),
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

    public record OpportunityAuthorityResponse(
            UUID opportunityId, long opportunityVersion, UUID accountId, UUID marketId,
            String instrument, String direction, UUID thesisId, long thesisVersion,
            String thesisState, UUID thesisMarketId, String thesisInstrument,
            java.time.Instant thesisValidFrom, java.time.Instant thesisValidUntil,
            java.time.Instant thesisSourceCutoff, java.time.Instant thesisEvaluatedAt) { }
}
