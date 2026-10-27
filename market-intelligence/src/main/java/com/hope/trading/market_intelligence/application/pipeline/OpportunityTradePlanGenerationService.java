package com.hope.trading.market_intelligence.application.pipeline;

import com.hope.trading.market_intelligence.adapter.marketdata.MarketDataClient;
import com.hope.trading.market_intelligence.adapter.marketdata.MarketPriceSnapshotRequest;
import com.hope.trading.market_intelligence.adapter.marketdata.MarketPriceSnapshotResponse;
import com.hope.trading.market_intelligence.application.port.TradePlanningContextRepository;
import com.hope.trading.market_intelligence.application.port.TradingOpportunityRepository;
import com.hope.trading.market_intelligence.application.port.TradePlanRepository;
import com.hope.trading.market_intelligence.application.port.MarketThesisRepository;
import com.hope.trading.market_intelligence.application.tradeplan.TradePlanApplicationService;
import com.hope.trading.market_intelligence.application.tradeplan.TradePlanningResult;
import com.hope.trading.market_intelligence.application.tradeplan.TradePlanningRequest;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityId;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityStatus;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlan;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanningContext;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Opportunity-direct trade plan generation (STORY-0023). Mirrors the
 * analysis-based generation: deterministic planning inputs only — ACTIVE
 * opportunity, fresh server-side market price, caller-supplied planning
 * context captured from the trader's effective profile.
 */
@Service
public class OpportunityTradePlanGenerationService {
    private final TradingOpportunityRepository opportunities;
    private final MarketThesisRepository theses;
    private final TradePlanningContextRepository contexts;
    private final MarketDataClient marketData;
    private final TradePlanApplicationService tradePlans;
    private final TradePlanRepository plans;
    private final Clock clock;
    private final Duration maximumPriceAge;
    private final PlanningPriceSelector priceSelector = new PlanningPriceSelector();

    @Autowired
    public OpportunityTradePlanGenerationService(
            TradingOpportunityRepository opportunities,
            MarketThesisRepository theses,
            TradePlanningContextRepository contexts,
            MarketDataClient marketData,
            TradePlanApplicationService tradePlans, TradePlanRepository plans, Clock clock,
            @Value("${intelligence.planning.price-max-age:30s}") Duration maximumPriceAge) {
        this.opportunities = opportunities;
        this.theses = theses;
        this.contexts = contexts;
        this.marketData = marketData;
        this.tradePlans = tradePlans;
        this.plans = plans;
        this.clock = clock;
        this.maximumPriceAge = maximumPriceAge;
    }

    public OpportunityTradePlanGenerationService(
            TradingOpportunityRepository opportunities,
            TradePlanningContextRepository contexts,
            MarketDataClient marketData,
            TradePlanApplicationService tradePlans, TradePlanRepository plans, Clock clock,
            Duration maximumPriceAge) {
        this(opportunities, null, contexts, marketData, tradePlans, plans, clock, maximumPriceAge);
    }

    public GenerationResponse generate(
            UUID opportunityId, UUID actorId, UUID accountId, TradePlanningContext context) {
        var opportunity = opportunities.findLatest(new OpportunityId(opportunityId))
                .orElseThrow(() -> failure(HttpStatus.UNPROCESSABLE_ENTITY, "OPPORTUNITY_NOT_ELIGIBLE"));
        return generate(opportunityId, actorId, opportunity.version().value(), accountId, context);
    }

    @Transactional
    public GenerationResponse generate(
            UUID opportunityId, UUID actorId, long opportunityVersion,
            UUID accountId, TradePlanningContext context) {
        if (!context.ownerId().equals(actorId)
                || !context.tradingAccountId().equals(accountId)) {
            throw failure(HttpStatus.FORBIDDEN, "PLANNING_CONTEXT_FORBIDDEN");
        }
        var opportunity = opportunities.find(new OpportunityId(opportunityId),
                        new com.hope.trading.market_intelligence.domain.opportunity.OpportunityVersion(
                                opportunityVersion))
                .filter(item -> item.status() == OpportunityStatus.ACTIVE)
                .orElseThrow(() -> failure(HttpStatus.UNPROCESSABLE_ENTITY, "OPPORTUNITY_NOT_ELIGIBLE"));
        if (theses != null) {
            var thesisReference = opportunity.marketThesis()
                    .orElseThrow(() -> failure(HttpStatus.UNPROCESSABLE_ENTITY,
                            "MARKET_THESIS_LINEAGE_UNAVAILABLE"));
            var thesis = theses.findByIdAndVersion(thesisReference.thesisId(), thesisReference.thesisVersion())
                    .orElseThrow(() -> failure(HttpStatus.UNPROCESSABLE_ENTITY,
                            "MARKET_THESIS_UNAVAILABLE"));
            if (thesis.validUntil().map(value -> !value.isAfter(clock.instant())).orElse(false)) {
                throw failure(HttpStatus.UNPROCESSABLE_ENTITY, "MARKET_THESIS_EXPIRED");
            }
            if (thesis.validFrom().isAfter(clock.instant())) {
                throw failure(HttpStatus.UNPROCESSABLE_ENTITY, "MARKET_THESIS_NOT_YET_VALID");
            }
            if (thesis.sourceCutoff().isAfter(clock.instant())
                    || thesis.evaluatedAt().isAfter(clock.instant())) {
                throw failure(HttpStatus.UNPROCESSABLE_ENTITY, "MARKET_THESIS_FRESHNESS_INVALID");
            }
            if (!thesis.marketId().equals(opportunity.marketId().orElse(null))
                    || !thesis.instrument().equalsIgnoreCase(opportunity.instrument())) {
                throw failure(HttpStatus.UNPROCESSABLE_ENTITY, "MARKET_THESIS_OPPORTUNITY_MISMATCH");
            }
            thesis.scenario().ifPresent(scenario -> {
                boolean compatible = scenario.direction() == com.hope.trading.market_intelligence.domain.thesis.MarketThesisScenario.Direction.NONE
                        || (scenario.direction() == com.hope.trading.market_intelligence.domain.thesis.MarketThesisScenario.Direction.LONG
                        && opportunity.direction() == com.hope.trading.market_intelligence.domain.opportunity.OpportunityDirection.LONG)
                        || (scenario.direction() == com.hope.trading.market_intelligence.domain.thesis.MarketThesisScenario.Direction.SHORT
                        && opportunity.direction() == com.hope.trading.market_intelligence.domain.opportunity.OpportunityDirection.SHORT);
                if (!compatible) {
                    throw failure(HttpStatus.UNPROCESSABLE_ENTITY, "MARKET_THESIS_OPPORTUNITY_MISMATCH");
                }
            });
            if (thesis.state() != com.hope.trading.market_intelligence.domain.thesis.MarketThesisState.CANDIDATE) {
                throw failure(HttpStatus.UNPROCESSABLE_ENTITY,
                        "MARKET_THESIS_PREPARATION_BLOCKED_" + thesis.state().name());
            }
        }
        UUID opportunityAccountId = opportunity.accountId().orElse(null);
        if (opportunityAccountId == null || !opportunityAccountId.equals(accountId)) {
            throw failure(HttpStatus.FORBIDDEN, "OPPORTUNITY_ACCOUNT_MISMATCH");
        }
        var existing = plans.findLatestForOpportunity(new OpportunityId(opportunityId)).stream()
                .filter(plan -> plan.rationale().opportunities().stream()
                        .anyMatch(reference -> reference.id().value().equals(opportunityId)
                                && reference.version().value() == opportunityVersion))
                .filter(plan -> plan.authorId().map(actorId::equals).orElse(false))
                .filter(plan -> contexts.find(plan.planningContext().id(), plan.planningContext().version())
                        .map(saved -> saved.tradingAccountId().equals(accountId)).orElse(false))
                .findFirst();
        if (existing.isPresent()) {
            TradePlan plan = existing.get();
            return new GenerationResponse(plan.id().value(), plan.version().value(), true);
        }
        var allMarkets = marketData.findAllMarkets();
        UUID marketId = opportunity.marketId().orElseGet(() -> {
            var matches = allMarkets.stream()
                    .filter(item -> opportunity.instrument().equalsIgnoreCase(item.symbol()))
                    .toList();
            if (matches.size() != 1) {
                throw failure(HttpStatus.UNPROCESSABLE_ENTITY, matches.isEmpty()
                        ? "MARKET_NOT_FOUND" : "MARKET_IDENTITY_AMBIGUOUS");
            }
            return matches.getFirst().marketId();
        });
        var market = marketData.findAllMarkets().stream()
                .filter(item -> item.marketId().equals(marketId))
                .findFirst()
                .orElseThrow(() -> failure(HttpStatus.UNPROCESSABLE_ENTITY, "MARKET_NOT_FOUND"));
        if (!opportunity.instrument().equalsIgnoreCase(market.symbol())) {
            throw failure(HttpStatus.UNPROCESSABLE_ENTITY, "OPPORTUNITY_MARKET_MISMATCH");
        }
        MarketPriceSnapshotResponse price = marketData.findPriceSnapshots(
                        new MarketPriceSnapshotRequest(List.of(marketId)))
                .stream().findFirst().orElseThrow(() -> failure(
                        HttpStatus.SERVICE_UNAVAILABLE, "MARKET_PRICE_UNAVAILABLE"));
        validatePrice(price, opportunity.instrument());
        PlanningPriceSelector.Selection selection;
        try {
            selection = priceSelector.select(opportunity.direction(), price.bid(), price.ask());
        } catch (IllegalArgumentException exception) {
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, "MARKET_PRICE_SIDE_UNAVAILABLE");
        }
        contexts.saveSnapshot(context);
        TradePlanningResult result = tradePlans.create(new TradePlanningRequest(
                Set.of(new OpportunityId(opportunityId)), context.id(), context.version(),
                actorId, selection.price(), null, null, null, Map.of(),
                market.provider(), market.productType()));
        if (!(result instanceof TradePlanningResult.Success success)) {
            TradePlanningResult.Failure failed = (TradePlanningResult.Failure) result;
            throw failure(HttpStatus.UNPROCESSABLE_ENTITY, failed.reason().name());
        }
        TradePlan plan = success.plan();
        return new GenerationResponse(plan.id().value(), plan.version().value(), false);
    }

    private void validatePrice(MarketPriceSnapshotResponse price, String instrument) {
        if ("STALE".equals(price.status())) {
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, "MARKET_PRICE_STALE");
        }
        if (!"FRESH".equals(price.status()) || !price.tradable()
                || price.sourceSnapshotId() == null || price.sourceSnapshotVersion() == null
                || price.capturedAt() == null || price.occurredAt() == null
                || !instrument.equalsIgnoreCase(price.symbol())) {
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, "MARKET_PRICE_UNAVAILABLE");
        }
        if (price.occurredAt().isBefore(clock.instant().minus(maximumPriceAge))) {
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, "MARKET_PRICE_STALE");
        }
    }

    private ResponseStatusException failure(HttpStatus status, String code) {
        return new ResponseStatusException(status, code);
    }

    public record GenerationResponse(UUID tradePlanId, long tradePlanVersion, boolean reused) { }
}
