package com.hope.trading.market_intelligence.application.pipeline;

import com.hope.trading.market_intelligence.adapter.marketdata.*;
import com.hope.trading.market_intelligence.adapter.persistence.*;
import com.hope.trading.market_intelligence.adapter.web.InternalAnalysisTradePlanRequest;
import com.hope.trading.market_intelligence.application.port.*;
import com.hope.trading.market_intelligence.application.tradeplan.*;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecutionStatus;
import com.hope.trading.market_intelligence.domain.opportunity.*;
import com.hope.trading.market_intelligence.domain.tradeplan.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import feign.FeignException;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class AnalysisTradePlanGenerationService {
    private final AnalysisExecutionRepository analyses;
    private final JpaIntelligencePipelineRunRepository pipelineRuns;
    private final TradingOpportunityRepository opportunities;
    private final TradePlanningContextRepository contexts;
    private final MarketDataClient marketData;
    private final TradePlanApplicationService tradePlans;
    private final JpaAnalysisTradePlanGenerationRepository generations;
    private final Clock clock;
    private final Duration maximumPriceAge;
    private final PlanningPriceSelector priceSelector = new PlanningPriceSelector();

    public AnalysisTradePlanGenerationService(
            AnalysisExecutionRepository analyses,
            JpaIntelligencePipelineRunRepository pipelineRuns,
            TradingOpportunityRepository opportunities,
            TradePlanningContextRepository contexts, MarketDataClient marketData,
            TradePlanApplicationService tradePlans,
            JpaAnalysisTradePlanGenerationRepository generations, Clock clock,
            @Value("${intelligence.planning.price-max-age:30s}") Duration maximumPriceAge) {
        this.analyses = analyses; this.pipelineRuns = pipelineRuns;
        this.opportunities = opportunities; this.contexts = contexts;
        this.marketData = marketData; this.tradePlans = tradePlans;
        this.generations = generations; this.clock = clock;
        this.maximumPriceAge = maximumPriceAge;
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public synchronized GenerationResponse generate(
            UUID analysisId, String idempotencyKey,
            InternalAnalysisTradePlanRequest request) {
        if (!request.actorId().equals(request.context().ownerId())
                || !request.accountId().equals(request.context().tradingAccountId())) {
            throw failure(HttpStatus.FORBIDDEN, "PLANNING_CONTEXT_FORBIDDEN");
        }
        Optional<JpaAnalysisTradePlanGenerationEntity> replay = generations
                .findByAnalysisExecutionIdAndActorIdAndAccountIdAndIdempotencyKey(
                        analysisId, request.actorId(), request.accountId(), idempotencyKey);
        if (replay.isPresent()) {
            JpaAnalysisTradePlanGenerationEntity value = replay.get();
            if (!value.contextId().equals(request.context().id())
                    || value.contextVersion() != request.context().version()) {
                throw failure(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT");
            }
            if ("COMPLETED".equals(value.state())) {
                return new GenerationResponse(value.tradePlanId(), value.tradePlanVersion());
            }
            if ("FAILED".equals(value.state())) {
                throw failure(HttpStatus.CONFLICT, value.failureCode());
            }
            throw failure(HttpStatus.CONFLICT, "GENERATION_IN_PROGRESS");
        }
        TradePlanningContext context = context(request.context());
        contexts.saveSnapshot(context);
        JpaAnalysisTradePlanGenerationEntity generation = generations.save(
                JpaAnalysisTradePlanGenerationEntity.running(
                        analysisId, request.actorId(), request.accountId(), context.id(),
                        context.version(), idempotencyKey, clock.instant()));
        try {
            var analysis = analyses.findById(analysisId)
                    .orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "ANALYSIS_NOT_FOUND"));
            if (analysis.status() != AnalysisExecutionStatus.COMPLETED) {
                throw failure(HttpStatus.CONFLICT, "ANALYSIS_NOT_COMPLETE");
            }
            JpaIntelligencePipelineRunEntity pipeline = pipelineRuns
                    .findByAnalysisExecutionIdAndPipelineVersion(
                            analysisId, ProductionIntelligencePipeline.VERSION)
                    .orElseThrow(() -> failure(
                            HttpStatus.UNPROCESSABLE_ENTITY, "PIPELINE_NOT_COMPLETE"));
            if (!"COMPLETED".equals(pipeline.state())) {
                throw failure(HttpStatus.UNPROCESSABLE_ENTITY, pipeline.state());
            }
            OpportunityId opportunityId = new OpportunityId(pipeline.opportunityId());
            TradingOpportunity opportunity = opportunities.find(
                            opportunityId, new OpportunityVersion(pipeline.opportunityVersion()))
                    .filter(item -> item.status() == OpportunityStatus.ACTIVE)
                    .orElseThrow(() -> failure(
                            HttpStatus.UNPROCESSABLE_ENTITY, "OPPORTUNITY_NOT_ELIGIBLE"));
            MarketPriceSnapshotResponse price = marketData.findPriceSnapshots(
                            new MarketPriceSnapshotRequest(List.of(analysis.provenance().marketId())))
                    .stream().findFirst().orElseThrow(() -> failure(
                            HttpStatus.SERVICE_UNAVAILABLE, "MARKET_PRICE_UNAVAILABLE"));
            validatePrice(price, opportunity.instrument());
            PlanningPriceSelector.Selection selection;
            try {
                selection = priceSelector.select(
                        opportunity.direction(), price.bid(), price.ask());
            } catch (IllegalArgumentException exception) {
                throw failure(HttpStatus.SERVICE_UNAVAILABLE, "MARKET_PRICE_SIDE_UNAVAILABLE");
            }
            String side = selection.side();
            BigDecimal selected = selection.price();
            TradePlanningResult result = tradePlans.create(new TradePlanningRequest(
                    Set.of(opportunityId), context.id(), context.version(), request.actorId(),
                    selected, null, null, null));
            if (!(result instanceof TradePlanningResult.Success success)) {
                TradePlanningResult.Failure failed = (TradePlanningResult.Failure) result;
                throw failure(HttpStatus.UNPROCESSABLE_ENTITY, failed.reason().name());
            }
            TradePlan plan = success.plan();
            generation.succeed(
                    price.sourceSnapshotId(), price.sourceSnapshotVersion(), price.capturedAt(),
                    price.occurredAt(), price.symbol(), selected, side, price.bid(), price.ask(),
                    price.lastPrice(), opportunity.id().value(), opportunity.version().value(),
                    plan.id().value(), plan.version().value(), clock.instant());
            generations.save(generation);
            return new GenerationResponse(plan.id().value(), plan.version().value());
        } catch (ResponseStatusException exception) {
            generation.fail(exception.getReason() == null
                    ? "GENERATION_FAILED" : exception.getReason(), clock.instant());
            generations.save(generation);
            throw exception;
        } catch (FeignException exception) {
            generation.fail("DEPENDENCY_UNAVAILABLE", clock.instant());
            generations.save(generation);
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE");
        }
    }

    private TradePlanningContext context(InternalAnalysisTradePlanRequest.Context value) {
        var budget = value.riskBudget(); var preferences = value.preferences();
        return new TradePlanningContext(
                value.id(), value.version(), value.capturedAt(), value.ownerId(),
                value.tradingAccountId(), value.accountCurrency(),
                new RiskBudget(budget.amount(), budget.currency(), budget.sourceId(),
                        budget.sourceVersion()),
                new PlanningPreferences(
                        preferences.id(), preferences.version(),
                        EntryType.valueOf(preferences.entryType()),
                        PlanningPreferences.StopStrategy.valueOf(preferences.stopStrategy()),
                        preferences.stopDistancePercent(),
                        PlanningPreferences.TargetStrategy.valueOf(preferences.targetStrategy()),
                        preferences.targetRiskMultiple(),
                        PlanningPreferences.PlanningHorizon.valueOf(preferences.horizon()),
                        preferences.validity()));
    }

    private void validatePrice(MarketPriceSnapshotResponse price, String instrument) {
        if (!"AVAILABLE".equals(price.status()) || !price.tradable()
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
