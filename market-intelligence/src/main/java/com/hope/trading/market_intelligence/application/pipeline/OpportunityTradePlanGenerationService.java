package com.hope.trading.market_intelligence.application.pipeline;

import com.hope.trading.market_intelligence.adapter.marketdata.MarketDataClient;
import com.hope.trading.market_intelligence.adapter.marketdata.MarketPriceSnapshotRequest;
import com.hope.trading.market_intelligence.adapter.marketdata.MarketPriceSnapshotResponse;
import com.hope.trading.market_intelligence.application.port.TradePlanningContextRepository;
import com.hope.trading.market_intelligence.application.port.TradingOpportunityRepository;
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
import java.util.UUID;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
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
    private final TradePlanningContextRepository contexts;
    private final MarketDataClient marketData;
    private final TradePlanApplicationService tradePlans;
    private final Clock clock;
    private final Duration maximumPriceAge;
    private final PlanningPriceSelector priceSelector = new PlanningPriceSelector();

    public OpportunityTradePlanGenerationService(
            TradingOpportunityRepository opportunities,
            TradePlanningContextRepository contexts,
            MarketDataClient marketData,
            TradePlanApplicationService tradePlans, Clock clock,
            @Value("${intelligence.planning.price-max-age:30s}") Duration maximumPriceAge) {
        this.opportunities = opportunities;
        this.contexts = contexts;
        this.marketData = marketData;
        this.tradePlans = tradePlans;
        this.clock = clock;
        this.maximumPriceAge = maximumPriceAge;
    }

    @Transactional
    public GenerationResponse generate(
            UUID opportunityId, UUID actorId, UUID accountId, TradePlanningContext context) {
        if (!context.ownerId().equals(actorId)
                || !context.tradingAccountId().equals(accountId)) {
            throw failure(HttpStatus.FORBIDDEN, "PLANNING_CONTEXT_FORBIDDEN");
        }
        var opportunity = opportunities.findLatest(new OpportunityId(opportunityId))
                .filter(item -> item.status() == OpportunityStatus.ACTIVE)
                .orElseThrow(() -> failure(HttpStatus.UNPROCESSABLE_ENTITY, "OPPORTUNITY_NOT_ELIGIBLE"));
        UUID marketId = marketData.findAllMarkets().stream()
                .filter(market -> opportunity.instrument().equalsIgnoreCase(market.symbol()))
                .map(com.hope.trading.market_intelligence.adapter.marketdata.MarketResponse::marketId)
                .findFirst()
                .orElseThrow(() -> failure(HttpStatus.UNPROCESSABLE_ENTITY, "MARKET_NOT_FOUND"));
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
                actorId, selection.price(), null, null, null));
        if (!(result instanceof TradePlanningResult.Success success)) {
            TradePlanningResult.Failure failed = (TradePlanningResult.Failure) result;
            throw failure(HttpStatus.UNPROCESSABLE_ENTITY, failed.reason().name());
        }
        TradePlan plan = success.plan();
        return new GenerationResponse(plan.id().value(), plan.version().value());
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

    public record GenerationResponse(UUID tradePlanId, long tradePlanVersion) { }
}
