package com.hope.trading.market_intelligence.application.passive;

import com.hope.trading.market_intelligence.adapter.marketdata.MarketDataClient;
import com.hope.trading.market_intelligence.adapter.marketdata.MarketResponse;
import com.hope.trading.market_intelligence.application.port.MarketThesisRepository;
import com.hope.trading.market_intelligence.application.port.ObservationRepository;
import com.hope.trading.market_intelligence.application.port.TradingOpportunityRepository;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityOrigin;
import com.hope.trading.market_intelligence.domain.opportunity.TradingOpportunity;
import com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public final class PassiveAwarenessProjectionService {
    private static final int MAX_SOURCE_ITEMS = 1_000;
    private final TradingOpportunityRepository opportunities;
    private final PassiveCandidateEvaluator evaluator;
    private final Clock clock;

    @Autowired
    public PassiveAwarenessProjectionService(
            TradingOpportunityRepository opportunities,
            PassiveCandidateEvaluator evaluator,
            Clock clock) {
        this.opportunities = Objects.requireNonNull(opportunities);
        this.evaluator = Objects.requireNonNull(evaluator);
        this.clock = Objects.requireNonNull(clock);
    }

    public PassiveAwarenessProjectionService(
            TradingOpportunityRepository opportunities,
            ObservationRepository observations,
            MarketThesisRepository theses,
            StrategyMatchRepository strategyMatches,
            MarketDataClient marketData,
            Clock clock) {
        this(opportunities,
                new PassiveCandidateEvaluator(opportunities, observations, theses, strategyMatches, marketData),
                clock);
    }

    public PassiveAwarenessPage search(PassiveAwarenessQuery query) {
        Instant now = clock.instant();
        List<TradingOpportunity> loadedSource = opportunities.findLatestPassive(MAX_SOURCE_ITEMS + 1);
        boolean truncated = loadedSource.size() > MAX_SOURCE_ITEMS;
        List<TradingOpportunity> source = loadedSource.stream()
                .limit(MAX_SOURCE_ITEMS)
                .filter(value -> value.origin() == OpportunityOrigin.PASSIVE_SCAN)
                .toList();
        List<PassiveAwarenessProjection> filtered = evaluator.evaluate(source, now).stream()
                .filter(value -> matches(value, query))
                .filter(value -> !query.currentOnly() || value.current())
                .sorted(Comparator
                        .comparing((PassiveAwarenessProjection value) -> value.opportunity().evaluatedAt())
                        .reversed()
                        .thenComparing(value -> value.opportunity().instrument())
                        .thenComparing(value -> value.opportunity().id().value()))
                .toList();
        int from = Math.min(query.page() * query.size(), filtered.size());
        int to = Math.min(from + query.size(), filtered.size());
        return new PassiveAwarenessPage(filtered.subList(from, to), query.page(), query.size(),
                filtered.size(), truncated);
    }

    private boolean matches(PassiveAwarenessProjection value, PassiveAwarenessQuery query) {
        MarketResponse market = value.market();
        return (query.instrument() == null || value.opportunity().instrument()
                .equalsIgnoreCase(query.instrument()))
                && (query.provider() == null || market != null
                && query.provider().equalsIgnoreCase(market.provider()))
                && (query.productType() == null || market != null
                && query.productType().equalsIgnoreCase(market.productType()));
    }
}
