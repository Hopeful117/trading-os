package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.scan.ActiveScanResultProjection;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecutionStatus;
import com.hope.trading.market_intelligence.domain.execution.AnalysisResultQuality;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityDirection;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityFactory;
import com.hope.trading.market_intelligence.domain.opportunity.*;
import com.hope.trading.market_intelligence.strategy.application.BuiltinStrategies;
import com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository;
import com.hope.trading.market_intelligence.strategy.domain.ConditionResult;
import com.hope.trading.market_intelligence.strategy.domain.MatchedDirection;
import com.hope.trading.market_intelligence.strategy.domain.StrategyId;
import com.hope.trading.market_intelligence.strategy.domain.StrategyMatch;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 0012 projection enrichment: minimal truthful Strategy provenance
 * (strategyMatchId/strategyId/strategyVersion) on the ActiveScan opportunity
 * projection; historical rows without a match project null cleanly.
 */
class ActiveScanStrategyProvenanceProjectionTest {

    private static final Instant NOW = Instant.parse("2026-08-21T10:00:00Z");
    private static final UUID MATCH_ID = UUID.fromString("bbbbbbbb-2222-3333-4444-555555555555");

    private final OpportunityFactory factory = new OpportunityFactory();

    private TradingOpportunity opportunity(UUID matchId) {
        return factory.create(
                new OpportunityId(UUID.randomUUID()), new OpportunityVersion(1),
                OpportunityStatus.ACTIVE, "ETH/USD", OpportunityDirection.LONG,
                "OHLC_TREND", "15m", OpportunityType.INTRADAY,
                OpportunityOrigin.PASSIVE_SCAN, new OpportunityScore(BigDecimal.TEN),
                "OHLC_TREND",
                Set.of(new ObservationReference(UUID.randomUUID())), Set.of(),
                NOW, NOW, NOW.plusSeconds(1800), NOW, matchId, null);
    }

    private StrategyMatch match() {
        return StrategyMatch.rehydrate(
                MATCH_ID, new StrategyId(BuiltinStrategies.LEGACY_OHLC_TREND_ID), 1,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                MatchedDirection.LONG, "digest", List.of(), NOW, NOW);
    }

    private ActiveScanResultProjection.MarketResult market(TradingOpportunity opportunity) {
        return new ActiveScanResultProjection.MarketResult(
                UUID.randomUUID(), 0, UUID.randomUUID(), true,
                List.of(), UUID.randomUUID(), AnalysisExecutionStatus.COMPLETED,
                AnalysisResultQuality.COMPLETE,
                com.hope.trading.market_intelligence.application.scan.ActiveScanMarketOutcome.OPPORTUNITY_FOUND,
                null, opportunity);
    }

    @Test
    void matchDerivedOpportunityProjectsFullStrategyProvenance() {
        var repository = new StrategyMatchRepository() {
            @Override public Optional<StrategyMatch> findById(UUID id) {
                return Optional.of(match());
            }
            @Override public Optional<StrategyMatch> findByIdentity(
                    com.hope.trading.market_intelligence.strategy.domain.StrategyMatchIdentity identity) {
                return Optional.empty();
            }
            @Override public List<StrategyMatch> findByAnalysisExecutionId(UUID id) {
                return List.of();
            }
            @Override public StrategyMatch save(StrategyMatch m) { return m; }
        };

        var response = ActiveScanResponse.from(
                new ActiveScanResultProjection(UUID.randomUUID(), UUID.randomUUID(), "obj",
                        com.hope.trading.market_intelligence.domain.scan.ActiveScanStatus.COMPLETED, List.of(), List.of(), List.of(),
                        NOW, NOW, NOW,
                        new ActiveScanResultProjection.ActiveScanProgress(1, 1, 0, 0, 1, 0, 1),
                        List.of(market(opportunity(MATCH_ID)))),
                repository);

        var strategy = response.markets().get(0).strategy();
        assertThat(strategy).isNotNull();
        assertThat(strategy.strategyMatchId()).isEqualTo(MATCH_ID);
        assertThat(strategy.strategyId()).isEqualTo(BuiltinStrategies.LEGACY_OHLC_TREND_ID);
        assertThat(strategy.strategyVersion()).isEqualTo(1);
        // no internal payloads exposed
        assertThat(response.toString()).doesNotContain("contextDigest", "conditionResults");
    }

    @Test
    void historicalOpportunityWithoutMatchProjectsNullProvenance() {
        var response = ActiveScanResponse.from(
                new ActiveScanResultProjection(UUID.randomUUID(), UUID.randomUUID(), "obj",
                        com.hope.trading.market_intelligence.domain.scan.ActiveScanStatus.COMPLETED, List.of(), List.of(), List.of(),
                        NOW, NOW, NOW,
                        new ActiveScanResultProjection.ActiveScanProgress(1, 1, 0, 0, 1, 0, 1),
                        List.of(market(opportunity(null)))),
                new StrategyMatchRepository() {
                    @Override public Optional<StrategyMatch> findById(UUID id) { return Optional.empty(); }
                    @Override public Optional<StrategyMatch> findByIdentity(
                            com.hope.trading.market_intelligence.strategy.domain.StrategyMatchIdentity identity) {
                        return Optional.empty();
                    }
                    @Override public List<StrategyMatch> findByAnalysisExecutionId(UUID id) { return List.of(); }
                    @Override public StrategyMatch save(StrategyMatch m) { return m; }
                });

        var market = response.markets().get(0);
        assertThat(market.opportunity().strategyMatchId()).isNull();
        assertThat(market.strategy()).isNull();
    }

    @Test
    void missingMarketOpportunityStillProjectsWithNullFields() {
        var response = ActiveScanResponse.from(
                new ActiveScanResultProjection(UUID.randomUUID(), UUID.randomUUID(), "obj",
                        com.hope.trading.market_intelligence.domain.scan.ActiveScanStatus.COMPLETED, List.of(), List.of(), List.of(),
                        NOW, NOW, NOW,
                        new ActiveScanResultProjection.ActiveScanProgress(1, 1, 0, 0, 1, 0, 0),
                        List.of(new ActiveScanResultProjection.MarketResult(
                                UUID.randomUUID(), 0, UUID.randomUUID(), true, List.of(),
                                UUID.randomUUID(), AnalysisExecutionStatus.COMPLETED,
                                AnalysisResultQuality.COMPLETE,
                                com.hope.trading.market_intelligence.application.scan.ActiveScanMarketOutcome.COMPLETED_NO_OPPORTUNITY,
                                null, null))),
                null);

        var market = response.markets().get(0);
        assertThat(market.opportunity()).isNull();
        assertThat(market.strategy()).isNull();
    }
}
