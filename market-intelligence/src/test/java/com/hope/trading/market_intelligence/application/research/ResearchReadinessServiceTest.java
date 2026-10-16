package com.hope.trading.market_intelligence.application.research;

import com.hope.trading.market_intelligence.adapter.marketdata.HistoricalOhlcClient;
import com.hope.trading.market_intelligence.application.port.FeatureSnapshotRepository;
import com.hope.trading.market_intelligence.application.port.ObservedMarketOutcomeRepository;
import com.hope.trading.market_intelligence.config.ResearchLifecycleProperties;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcome;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcomeStatus;
import com.hope.trading.market_intelligence.domain.outcome.OutcomeHorizon;
import com.hope.trading.market_intelligence.domain.feature.FeatureSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResearchReadinessServiceTest {
    private static final UUID MARKET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-10-16T10:00:00Z");

    @Test
    void readinessCountsOnlyConfiguredOutcomeHorizons() {
        ResearchLifecycleProperties properties = new ResearchLifecycleProperties();
        properties.setEnabled(true);
        properties.setMarketIds(List.of(MARKET_ID));
        properties.setOutcomeHorizons(List.of(Duration.ofHours(1)));

        FeatureSnapshotRepository snapshots = mock(FeatureSnapshotRepository.class);
        when(snapshots.findByPopulation(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(List.of());
        ObservedMarketOutcomeRepository outcomes = mock(ObservedMarketOutcomeRepository.class);
        HistoricalOhlcClient historicalOhlc = mock(HistoricalOhlcClient.class);
        HistoricalOhlcClient.HistoricalOhlcEvidenceResponse evidence = mock(
                HistoricalOhlcClient.HistoricalOhlcEvidenceResponse.class);
        when(evidence.knownAt()).thenReturn(NOW.minus(Duration.ofMinutes(5)));
        when(historicalOhlc.findHistoricalOhlc(MARKET_ID, "FIFTEEN_MINUTES", NOW, 1))
                .thenReturn(new HistoricalOhlcClient.HistoricalOhlcSeriesResponse(
                        MARKET_ID, "FIFTEEN_MINUTES", NOW, List.of(evidence), "AVAILABLE", null));
        List<ObservedMarketOutcome> observedOutcomes = List.of(
                outcome(OutcomeHorizon.oneHour(), "configured"),
                outcome(OutcomeHorizon.fourHours(), "outside-config"));
        when(outcomes.findByMarkets(org.mockito.ArgumentMatchers.any()))
                .thenReturn(observedOutcomes);

        ResearchReadiness readiness = new ResearchReadinessService(
                properties, snapshots, outcomes, historicalOhlc,
                Clock.fixed(NOW, ZoneOffset.UTC)).current();

        assertThat(readiness.outcomeCountsByHorizonAndStatus())
                .containsOnlyKeys("PT1H:AVAILABLE")
                .containsEntry("PT1H:AVAILABLE", 1L);
        assertThat(readiness.eligibleOutcomeCount()).isEqualTo(1L);
        assertThat(readiness.approximateAvailableResearchPopulation()).isZero();
        assertThat(readiness.latestSuccessfulEvidenceKnownAt())
                .containsEntry(MARKET_ID, NOW.minus(Duration.ofMinutes(5)));
    }

    @Test
    void readinessComputesPendingAndAvailablePopulationCounts() {
        ResearchLifecycleProperties properties = new ResearchLifecycleProperties();
        properties.setEnabled(true);
        properties.setMarketIds(List.of(MARKET_ID));
        properties.setOutcomeHorizons(List.of(Duration.ofHours(1), Duration.ofHours(4)));

        FeatureSnapshot snapshot = mock(FeatureSnapshot.class);
        Instant cutoff = NOW.minus(Duration.ofHours(2));
        when(snapshot.marketId()).thenReturn(MARKET_ID);
        when(snapshot.evaluationCutoff()).thenReturn(cutoff);
        FeatureSnapshotRepository snapshots = mock(FeatureSnapshotRepository.class);
        when(snapshots.findByPopulation(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(List.of(snapshot));

        ObservedMarketOutcome available = mock(ObservedMarketOutcome.class);
        when(available.marketId()).thenReturn(MARKET_ID);
        when(available.evaluationCutoff()).thenReturn(cutoff);
        when(available.horizon()).thenReturn(OutcomeHorizon.oneHour());
        when(available.status()).thenReturn(ObservedMarketOutcomeStatus.AVAILABLE);
        ObservedMarketOutcomeRepository outcomes = mock(ObservedMarketOutcomeRepository.class);
        when(outcomes.findByMarkets(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(available));

        ResearchReadiness readiness = new ResearchReadinessService(
                properties, snapshots, outcomes, mock(HistoricalOhlcClient.class),
                Clock.fixed(NOW, ZoneOffset.UTC)).current();

        assertThat(readiness.pendingOutcomeCount()).isEqualTo(1L);
        assertThat(readiness.approximateAvailableResearchPopulation()).isEqualTo(1L);
    }

    @Test
    void readinessSupportsAnEnabledConfigurationWithoutMarketsOnlyWhenDisabled() {
        ResearchLifecycleProperties properties = new ResearchLifecycleProperties();
        FeatureSnapshotRepository snapshots = mock(FeatureSnapshotRepository.class);
        ObservedMarketOutcomeRepository outcomes = mock(ObservedMarketOutcomeRepository.class);
        when(outcomes.findByMarkets(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        ResearchReadiness readiness = new ResearchReadinessService(
                properties, snapshots, outcomes, mock(HistoricalOhlcClient.class),
                Clock.fixed(NOW, ZoneOffset.UTC)).current();

        assertThat(readiness.featureSnapshotCount()).isZero();
        assertThat(readiness.configuredMarkets()).isEmpty();
    }

    @Test
    void readinessRemainsAvailableWhenMarketDataIsTemporarilyUnavailable() {
        ResearchLifecycleProperties properties = new ResearchLifecycleProperties();
        properties.setEnabled(true);
        properties.setMarketIds(List.of(MARKET_ID));
        properties.setOutcomeHorizons(List.of(Duration.ofHours(1)));
        HistoricalOhlcClient historicalOhlc = mock(HistoricalOhlcClient.class);
        when(historicalOhlc.findHistoricalOhlc(MARKET_ID, "FIFTEEN_MINUTES", NOW, 1))
                .thenThrow(new RuntimeException("market data unavailable"));
        ObservedMarketOutcomeRepository outcomes = mock(ObservedMarketOutcomeRepository.class);
        when(outcomes.findByMarkets(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        ResearchReadiness readiness = new ResearchReadinessService(
                properties, mock(FeatureSnapshotRepository.class), outcomes, historicalOhlc,
                Clock.fixed(NOW, ZoneOffset.UTC)).current();

        assertThat(readiness.latestSuccessfulEvidenceKnownAt()).isEmpty();
    }

    @Test
    void readinessDoesNotCountPendingOutcomesAsAvailablePopulation() {
        ResearchLifecycleProperties properties = new ResearchLifecycleProperties();
        properties.setEnabled(true);
        properties.setMarketIds(List.of(MARKET_ID));
        properties.setOutcomeHorizons(List.of(Duration.ofHours(1)));
        FeatureSnapshot snapshot = mock(FeatureSnapshot.class);
        Instant cutoff = NOW.minus(Duration.ofHours(2));
        when(snapshot.marketId()).thenReturn(MARKET_ID);
        when(snapshot.evaluationCutoff()).thenReturn(cutoff);
        ObservedMarketOutcome pending = mock(ObservedMarketOutcome.class);
        when(pending.marketId()).thenReturn(MARKET_ID);
        when(pending.evaluationCutoff()).thenReturn(cutoff);
        when(pending.horizon()).thenReturn(OutcomeHorizon.oneHour());
        when(pending.status()).thenReturn(ObservedMarketOutcomeStatus.PENDING);
        FeatureSnapshotRepository snapshots = mock(FeatureSnapshotRepository.class);
        when(snapshots.findByPopulation(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(List.of(snapshot));
        ObservedMarketOutcomeRepository outcomes = mock(ObservedMarketOutcomeRepository.class);
        when(outcomes.findByMarkets(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(pending));

        ResearchReadiness readiness = new ResearchReadinessService(
                properties, snapshots, outcomes, mock(HistoricalOhlcClient.class),
                Clock.fixed(NOW, ZoneOffset.UTC)).current();

        assertThat(readiness.outcomeCountsByHorizonAndStatus())
                .containsOnlyKeys("PT1H:PENDING");
        assertThat(readiness.eligibleOutcomeCount()).isZero();
        assertThat(readiness.approximateAvailableResearchPopulation()).isZero();
    }

    private ObservedMarketOutcome outcome(OutcomeHorizon horizon, String fingerprint) {
        ObservedMarketOutcome outcome = mock(ObservedMarketOutcome.class);
        when(outcome.marketId()).thenReturn(MARKET_ID);
        when(outcome.evaluationCutoff()).thenReturn(NOW.minus(horizon.value()).minusSeconds(1));
        when(outcome.horizon()).thenReturn(horizon);
        when(outcome.status()).thenReturn(ObservedMarketOutcomeStatus.AVAILABLE);
        when(outcome.outcomeFingerprint()).thenReturn(fingerprint);
        when(outcome.forwardReturn()).thenReturn(BigDecimal.ZERO);
        return outcome;
    }
}
