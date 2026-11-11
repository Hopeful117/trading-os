package com.hope.trading.market_intelligence.application.research;

import com.hope.trading.market_intelligence.application.port.AnalysisExecutionRepository;
import com.hope.trading.market_intelligence.application.port.FeatureSnapshotRepository;
import com.hope.trading.market_intelligence.application.port.ObservedMarketOutcomeRepository;
import com.hope.trading.market_intelligence.domain.feature.FeatureSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HistoricalReadinessServiceTest {
    private static final UUID MARKET = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-11-08T12:00:00Z");

    @Test
    void reportsInsufficientWhenTheAuthorizedPopulationHasNoSnapshots() {
        FeatureSnapshotRepository snapshots = mock(FeatureSnapshotRepository.class);
        ObservedMarketOutcomeRepository outcomes = mock(ObservedMarketOutcomeRepository.class);
        AnalysisExecutionRepository executions = mock(AnalysisExecutionRepository.class);
        when(snapshots.findByMarkets(any(), any(), any())).thenReturn(List.of());
        when(outcomes.findByMarkets(any())).thenReturn(List.of());

        var result = new HistoricalReadinessService(snapshots, outcomes, executions,
                Clock.fixed(NOW, ZoneOffset.UTC)).inspect(new HistoricalReadinessRequest(
                UUID.randomUUID(), Set.of(MARKET), NOW.minusSeconds(3600), NOW, "profile-v1", 100), "corr-1");

        assertThat(result.verdict()).isEqualTo(HistoricalReadinessVerdict.INSUFFICIENT);
        assertThat(result.blockers()).contains("NO_FEATURE_SNAPSHOTS");
        assertThat(result.correlationId()).isEqualTo("corr-1");
    }

    @Test
    void truncatesSnapshotsAfterDetectingTheObservationLimit() {
        FeatureSnapshot first = mock(FeatureSnapshot.class);
        FeatureSnapshot second = mock(FeatureSnapshot.class);
        when(first.marketId()).thenReturn(MARKET);
        when(second.marketId()).thenReturn(MARKET);
        when(first.evaluationCutoff()).thenReturn(NOW.minusSeconds(1800));
        when(second.evaluationCutoff()).thenReturn(NOW.minusSeconds(900));

        FeatureSnapshotRepository snapshots = mock(FeatureSnapshotRepository.class);
        ObservedMarketOutcomeRepository outcomes = mock(ObservedMarketOutcomeRepository.class);
        AnalysisExecutionRepository executions = mock(AnalysisExecutionRepository.class);
        when(snapshots.findByPopulation(any(), any(), any(), any(), any(), any(Integer.class)))
                .thenReturn(List.of(first, second));
        when(outcomes.findByMarketsAndCutoffRange(any(), any(), any(), any(Integer.class)))
                .thenReturn(List.of());
        when(outcomes.countByMarketsAndCutoffRangeAndStatus(any(), any(), any(), any())).thenReturn(0L);
        when(snapshots.countByMarketsAfter(any(), any(), any(), any())).thenReturn(0L);

        var result = new HistoricalReadinessService(snapshots, outcomes, executions,
                Clock.fixed(NOW, ZoneOffset.UTC)).inspect(new HistoricalReadinessRequest(
                UUID.randomUUID(), Set.of(MARKET), NOW.minusSeconds(3600), NOW, "profile-v1", 1), "corr-2");

        assertThat(result.availableEvidenceCount()).isEqualTo(1);
        assertThat(result.featureSnapshotCount()).isEqualTo(1);
        assertThat(result.evidenceByMarket()).containsEntry(MARKET, 1L);
        assertThat(result.latestEligibleEvidence()).isEqualTo(NOW.minusSeconds(1800));
        assertThat(result.blockers()).contains("OBSERVATION_LIMIT_EXCEEDED");
    }

    @Test
    void requestsPopulationSnapshotsThroughTheStableRepositoryOrder() {
        FeatureSnapshotRepository snapshots = mock(FeatureSnapshotRepository.class);
        ObservedMarketOutcomeRepository outcomes = mock(ObservedMarketOutcomeRepository.class);
        AnalysisExecutionRepository executions = mock(AnalysisExecutionRepository.class);
        when(snapshots.findByPopulation(any(), any(), any(), any(), any(), any(Integer.class)))
                .thenReturn(List.of());
        when(outcomes.findByMarketsAndCutoffRange(any(), any(), any(), any(Integer.class)))
                .thenReturn(List.of());
        when(outcomes.countByMarketsAndCutoffRangeAndStatus(any(), any(), any(), any())).thenReturn(0L);
        when(snapshots.countByMarketsAfter(any(), any(), any(), any())).thenReturn(0L);

        new HistoricalReadinessService(snapshots, outcomes, executions, Clock.fixed(NOW, ZoneOffset.UTC))
                .inspect(new HistoricalReadinessRequest(UUID.randomUUID(), Set.of(MARKET),
                        NOW.minusSeconds(3600), NOW, "profile-v1", 100), "corr-3");

        org.mockito.Mockito.verify(snapshots).findByPopulation(Set.of(MARKET), NOW.minusSeconds(3600), NOW,
                "feature-projection-v1", "STRICT_OBSERVED", 101);
    }
}
