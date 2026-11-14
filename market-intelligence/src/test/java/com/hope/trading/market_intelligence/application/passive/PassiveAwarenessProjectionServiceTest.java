package com.hope.trading.market_intelligence.application.passive;

import com.hope.trading.market_intelligence.adapter.marketdata.MarketDataClient;
import com.hope.trading.market_intelligence.adapter.marketdata.MarketResponse;
import com.hope.trading.market_intelligence.application.opportunity.OpportunityTestFixtures;
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
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PassiveAwarenessProjectionServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-30T12:00:00Z");

    @Test
    void projectsOnlyAQualifyingPassiveCandidateAsCurrent() {
        TradingOpportunity opportunity = opportunity();
        Observation observation = currentObservation(opportunity.observations().iterator().next().observationId());
        var thesisReference = opportunity.marketThesis().orElseThrow();
        MarketThesis thesis = thesis(opportunity.marketId().orElseThrow(), opportunity.instrument(),
                thesisReference.thesisId());
        var opportunities = mock(TradingOpportunityRepository.class);
        var observations = mock(ObservationRepository.class);
        var theses = mock(MarketThesisRepository.class);
        var strategyMatches = mock(StrategyMatchRepository.class);
        var marketData = mock(MarketDataClient.class);
        when(opportunities.findLatestPassive(any(Integer.class))).thenReturn(List.of(opportunity));
        when(observations.findByIds(any())).thenReturn(List.of(observation));
        when(theses.findByIdAndVersions(any())).thenReturn(List.of(thesis));
        StrategyMatch match = mock(StrategyMatch.class);
        when(match.matchId()).thenReturn(opportunity.strategyMatchId().orElseThrow());
        when(match.marketId()).thenReturn(opportunity.marketId().orElseThrow());
        when(match.direction()).thenReturn(MatchedDirection.LONG);
        when(match.observationId()).thenReturn(opportunity.observations().iterator().next().observationId());
        when(match.analysisExecutionId()).thenReturn(opportunity.analysisExecutionId().orElseThrow());
        when(match.matchedAt()).thenReturn(NOW.minusSeconds(20));
        when(strategyMatches.findById(any())).thenReturn(Optional.of(match));
        when(strategyMatches.findByIds(any())).thenReturn(List.of(match));
        MarketResponse market = new MarketResponse(opportunity.marketId().orElseThrow(), "KRAKEN", "BTC/EUR",
                "BTC", "EUR", "SPOT", null);
        when(marketData.findMarketsByIds(any())).thenReturn(List.of(market));

        PassiveAwarenessPage page = service(opportunities, observations, theses, strategyMatches, marketData)
                .search(new PassiveAwarenessQuery(null, null, null, true, 0, 20));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.current()).isTrue();
            assertThat(item.diagnostics()).isEmpty();
            assertThat(item.thesis()).isEqualTo(thesis);
            assertThat(item.market().provider()).isEqualTo("KRAKEN");
        });
    }

    @Test
    void staleOrMissingEvidenceIsNonPositiveAndExcludedByCurrentOnly() {
        TradingOpportunity opportunity = opportunity();
        var opportunities = mock(TradingOpportunityRepository.class);
        var observations = mock(ObservationRepository.class);
        var theses = mock(MarketThesisRepository.class);
        var strategyMatches = mock(StrategyMatchRepository.class);
        var marketData = mock(MarketDataClient.class);
        when(opportunities.findLatestPassive(any(Integer.class))).thenReturn(List.of(opportunity));
        when(marketData.findMarketsByIds(any())).thenReturn(List.of(new MarketResponse(
                opportunity.marketId().orElseThrow(), "KRAKEN", "BTC/EUR",
                "BTC", "EUR", "SPOT", null)));

        PassiveAwarenessProjectionService service = service(
                opportunities, observations, theses, strategyMatches, marketData);
        PassiveAwarenessPage all = service.search(
                new PassiveAwarenessQuery(null, null, null, false, 0, 20));
        PassiveAwarenessPage current = service.search(
                new PassiveAwarenessQuery(null, null, null, true, 0, 20));

        assertThat(all.total()).isEqualTo(1);
        assertThat(all.items()).singleElement().satisfies(item -> {
            assertThat(item.current()).isFalse();
            assertThat(item.diagnostics()).contains("OBSERVATION_REFERENCE_UNAVAILABLE",
                    "MARKET_THESIS_REFERENCE_UNAVAILABLE");
        });
        assertThat(current.total()).isZero();
    }

    @Test
    void expiredRequiredThesisEvidenceIsNonPositive() {
        TradingOpportunity opportunity = opportunity();
        var opportunities = mock(TradingOpportunityRepository.class);
        var observations = mock(ObservationRepository.class);
        var theses = mock(MarketThesisRepository.class);
        var strategyMatches = mock(StrategyMatchRepository.class);
        var marketData = mock(MarketDataClient.class);
        MarketThesisReference reference = opportunity.marketThesis().orElseThrow();
        MarketThesis expired = thesis(opportunity.marketId().orElseThrow(), opportunity.instrument(),
                reference.thesisId(), NOW.minusSeconds(1));
        when(opportunities.findLatestPassive(any(Integer.class))).thenReturn(List.of(opportunity));
        Observation observation = currentObservation(opportunity.observations().iterator().next().observationId());
        when(observations.findByIds(any())).thenReturn(List.of(observation));
        when(theses.findByIdAndVersions(any())).thenReturn(List.of(expired));
        when(strategyMatches.findByIds(any())).thenReturn(List.of());
        when(marketData.findMarketsByIds(any())).thenReturn(List.of(new MarketResponse(
                opportunity.marketId().orElseThrow(), "KRAKEN", "BTC/EUR", "BTC", "EUR", "SPOT", null)));

        PassiveAwarenessPage page = service(opportunities, observations, theses, strategyMatches, marketData)
                .search(new PassiveAwarenessQuery(null, null, null, false, 0, 20));

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.current()).isFalse();
            assertThat(item.diagnostics()).contains("REQUIRED_EVIDENCE_EXPIRED");
        });
    }

    @Test
    void reportsWhenSourceItemsAreTruncated() {
        var opportunities = mock(TradingOpportunityRepository.class);
        var observations = mock(ObservationRepository.class);
        var theses = mock(MarketThesisRepository.class);
        var strategyMatches = mock(StrategyMatchRepository.class);
        var marketData = mock(MarketDataClient.class);
        List<TradingOpportunity> source = IntStream.range(0, 1_001)
                .mapToObj(ignored -> opportunity()).toList();
        when(opportunities.findLatestPassive(1_001)).thenReturn(source);
        when(marketData.findMarketsByIds(any())).thenReturn(List.of());

        PassiveAwarenessPage page = service(opportunities, observations, theses, strategyMatches, marketData)
                .search(new PassiveAwarenessQuery(null, null, null, false, 0, 20));

        assertThat(page.truncated()).isTrue();
        assertThat(page.total()).isEqualTo(1_000);
    }

    private PassiveAwarenessProjectionService service(
            TradingOpportunityRepository opportunities,
            ObservationRepository observations,
            MarketThesisRepository theses,
            StrategyMatchRepository strategyMatches,
            MarketDataClient marketData) {
        return new PassiveAwarenessProjectionService(
                opportunities, observations, theses, strategyMatches, marketData,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private TradingOpportunity opportunity() {
        UUID marketId = UUID.randomUUID();
        UUID observationId = UUID.randomUUID();
        UUID thesisId = UUID.randomUUID();
        return new OpportunityFactory().create(new OpportunityFactory.Values(
                new OpportunityId(UUID.randomUUID()), new OpportunityVersion(1),
                OpportunityStatus.ACTIVE, "BTC/EUR", OpportunityDirection.LONG,
                "Bullish breakout", "5m", OpportunityType.SCALPING,
                OpportunityOrigin.PASSIVE_SCAN, new OpportunityScore(BigDecimal.TEN),
                "Confirmed", java.util.Set.of(new ObservationReference(observationId)),
                java.util.Set.of(), NOW.minusSeconds(10), NOW.minusSeconds(10),
                NOW.plusSeconds(300), NOW.minusSeconds(10), UUID.randomUUID(), null,
                 marketId, null, UUID.randomUUID(), marketId, UUID.randomUUID(),
                new MarketThesisReference(thesisId, 1)));
    }

    private Observation currentObservation(UUID id) {
        Observation observation = mock(Observation.class);
        when(observation.id()).thenReturn(id);
        when(observation.status()).thenReturn(ObservationStatus.ACTIVE);
        when(observation.validFrom()).thenReturn(NOW.minusSeconds(30));
        when(observation.validUntil()).thenReturn(Optional.empty());
        when(observation.createdAt()).thenReturn(NOW.minusSeconds(20));
        return observation;
    }

    private MarketThesis thesis(UUID marketId, String instrument, UUID thesisId) {
        return thesis(marketId, instrument, thesisId, NOW.plusSeconds(300));
    }

    private MarketThesis thesis(UUID marketId, String instrument, UUID thesisId, Instant evidenceValidUntil) {
        return new MarketThesis(
                thesisId, UUID.randomUUID(), 1, marketId, instrument,
                MarketThesisState.CANDIDATE,
                new MarketThesisScenario(MarketThesisScenario.Direction.LONG,
                        "breakout", "Price confirms", "Structure fails", "100"),
                List.of(new MarketThesisEvidence(
                        UUID.randomUUID(), MarketThesisEvidenceRole.SUPPORTING,
                        MarketThesisAvailability.AVAILABLE, null, "TEST", "observation",
                         "5m", "Valid evidence", NOW.minusSeconds(20), evidenceValidUntil,
                         "KRAKEN", instrument, "fingerprint", "rule-v1", NOW.minusSeconds(10), true)),
                List.of(), NOW.minusSeconds(5), NOW.minusSeconds(10), NOW.minusSeconds(5),
                NOW.plusSeconds(300), UUID.randomUUID(), "composer-v1", "input-fingerprint", null);
    }
}
