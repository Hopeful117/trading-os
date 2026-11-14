package com.hope.trading.market_intelligence.application.passive;

import com.hope.trading.market_intelligence.adapter.marketdata.MarketDataClient;
import com.hope.trading.market_intelligence.adapter.marketdata.MarketResponse;
import com.hope.trading.market_intelligence.application.port.MarketThesisRepository;
import com.hope.trading.market_intelligence.application.port.ObservationRepository;
import com.hope.trading.market_intelligence.application.port.TradingOpportunityRepository;
import com.hope.trading.market_intelligence.domain.observation.Observation;
import com.hope.trading.market_intelligence.domain.observation.ObservationStatus;
import com.hope.trading.market_intelligence.domain.opportunity.*;
import com.hope.trading.market_intelligence.domain.thesis.*;
import com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository;
import com.hope.trading.market_intelligence.strategy.domain.MatchedDirection;
import com.hope.trading.market_intelligence.strategy.domain.StrategyMatch;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Deterministic passive-candidate fixture (Story 0243). Builds one qualifying passive
 * opportunity and wires mocked repositories; individual tests then degrade one aspect.
 */
public final class PassiveCandidateFixtures {
    public static final Instant NOW = Instant.parse("2026-07-30T12:00:00Z");

    public final TradingOpportunityRepository opportunities = mock(TradingOpportunityRepository.class);
    public final ObservationRepository observations = mock(ObservationRepository.class);
    public final MarketThesisRepository theses = mock(MarketThesisRepository.class);
    public final StrategyMatchRepository strategyMatches = mock(StrategyMatchRepository.class);
    public final MarketDataClient marketData = mock(MarketDataClient.class);

    public final UUID marketId = UUID.randomUUID();
    public final UUID observationId = UUID.randomUUID();
    public final UUID thesisId = UUID.randomUUID();
    public final UUID strategyMatchId = UUID.randomUUID();
    public final UUID passiveExecutionId = UUID.randomUUID();
    public final UUID passiveRunId = UUID.randomUUID();
    public TradingOpportunity opportunity;
    public MarketThesis thesis;

    public PassiveCandidateFixtures() {
        opportunity = opportunity(1, OpportunityOrigin.PASSIVE_SCAN, null, NOW.plusSeconds(300));
        thesis = thesis(MarketThesisState.CANDIDATE, MarketThesisEvidenceRole.SUPPORTING, NOW.minusSeconds(10));
        Observation observation = mock(Observation.class);
        when(observation.id()).thenReturn(observationId);
        when(observation.status()).thenReturn(ObservationStatus.ACTIVE);
        when(observation.validFrom()).thenReturn(NOW.minusSeconds(30));
        when(observation.validUntil()).thenReturn(Optional.empty());
        when(observation.createdAt()).thenReturn(NOW.minusSeconds(20));
        when(observations.findByIds(any())).thenReturn(List.of(observation));
        StrategyMatch match = mock(StrategyMatch.class);
        when(match.matchId()).thenReturn(strategyMatchId);
        when(match.marketId()).thenReturn(marketId);
        when(match.direction()).thenReturn(MatchedDirection.LONG);
        when(match.observationId()).thenReturn(observationId);
        when(match.analysisExecutionId()).thenReturn(passiveExecutionId);
        when(match.matchedAt()).thenReturn(NOW.minusSeconds(20));
        when(strategyMatches.findByIds(any())).thenReturn(List.of(match));
        when(marketData.findMarketsByIds(any())).thenReturn(List.of(
                new MarketResponse(marketId, "KRAKEN", "BTC/EUR", "BTC", "EUR", "SPOT", null)));
        when(theses.findSupersededThesisIds(any())).thenReturn(Set.of());
        install();
    }

    /** Re-registers the current opportunity/thesis as the stored latest versions. */
    public PassiveCandidateFixtures install() {
        when(opportunities.findLatestPassive(any(Integer.class))).thenReturn(List.of(opportunity));
        when(opportunities.find(eq(opportunity.id()), eq(opportunity.version()))).thenReturn(Optional.of(opportunity));
        when(opportunities.findLatest(opportunity.id())).thenReturn(Optional.of(opportunity));
        when(theses.findByIdAndVersions(any())).thenReturn(List.of(thesis));
        return this;
    }

    public PassiveCandidateEvaluator evaluator() {
        return new PassiveCandidateEvaluator(opportunities, observations, theses, strategyMatches, marketData);
    }

    public TradingOpportunity opportunity(long version, OpportunityOrigin origin, UUID accountId, Instant validUntil) {
        UUID id = opportunity == null ? UUID.randomUUID() : opportunity.id().value();
        return new OpportunityFactory().create(new OpportunityFactory.Values(
                new OpportunityId(id), new OpportunityVersion(version),
                OpportunityStatus.ACTIVE, "BTC/EUR", OpportunityDirection.LONG,
                "Bullish breakout", "5m", OpportunityType.SCALPING,
                origin, new OpportunityScore(BigDecimal.TEN),
                "Confirmed", Set.of(new ObservationReference(observationId)),
                Set.of(), NOW.minusSeconds(10), NOW.minusSeconds(10),
                validUntil, NOW.minusSeconds(10), strategyMatchId, null,
                marketId, accountId, passiveRunId, marketId, passiveExecutionId,
                new MarketThesisReference(thesisId, 1)));
    }

    public MarketThesis thesis(MarketThesisState state, MarketThesisEvidenceRole role, Instant sourceCutoff) {
        return thesis(state, role, null, sourceCutoff);
    }

    public MarketThesis thesis(MarketThesisState state, MarketThesisEvidenceRole role,
                               MarketThesisContradictionSeverity severity, Instant sourceCutoff) {
        return new MarketThesis(
                thesisId, UUID.randomUUID(), 1, marketId, "BTC/EUR", state,
                new MarketThesisScenario(MarketThesisScenario.Direction.LONG,
                        "breakout", "Price confirms", "Structure fails", "100"),
                List.of(new MarketThesisEvidence(
                        UUID.randomUUID(), role, MarketThesisAvailability.AVAILABLE, severity, "TEST",
                        "observation", "5m", "Evidence", sourceCutoff.minusSeconds(10), NOW.plusSeconds(300),
                        "KRAKEN", "BTC/EUR", "fingerprint", "rule-v1", sourceCutoff, true)),
                List.of(), NOW.minusSeconds(5), sourceCutoff, NOW.minusSeconds(5),
                NOW.plusSeconds(300), passiveExecutionId, "composer-v1", "input-fingerprint", null);
    }
}
