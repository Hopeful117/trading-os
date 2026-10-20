package com.hope.trading.market_intelligence.application.research;

import com.hope.trading.market_intelligence.application.port.FeatureSnapshotRepository;
import com.hope.trading.market_intelligence.application.port.ObservedMarketOutcomeRepository;
import com.hope.trading.market_intelligence.config.ResearchLifecycleProperties;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcome;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcomeStatus;
import com.hope.trading.market_intelligence.domain.outcome.OutcomeHorizon;
import com.hope.trading.market_intelligence.domain.feature.FeatureSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResearchIntegrityServiceTest {
    private static final UUID MARKET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-10-16T10:00:00Z");

    @Test
    void reportsHealthyFreshnessAndMaturedOutcomeEvidence() {
        ResearchLifecycleProperties properties = properties();
        FeatureSnapshotRepository snapshots = mock(FeatureSnapshotRepository.class);
        FeatureSnapshot latest = snapshot(NOW.minus(Duration.ofHours(1)));
        FeatureSnapshot candidate = snapshot(NOW.minus(Duration.ofHours(2)));
        when(snapshots.findLatestByMarket(any(), any(), anyString(), anyString()))
                .thenReturn(Optional.of(latest));
        when(snapshots.findByPopulation(any(), any(), any(), anyString(), anyString()))
                .thenReturn(List.of(candidate));

        ObservedMarketOutcomeRepository outcomes = mock(ObservedMarketOutcomeRepository.class);
        ObservedMarketOutcome available = outcome(
                candidate.evaluationCutoff(), ObservedMarketOutcomeStatus.AVAILABLE);
        when(outcomes.findByMarketsAndCutoffRange(any(), any(), any()))
                .thenReturn(List.of(available));

        ResearchIntegrity integrity = new ResearchIntegrityService(
                properties, snapshots, outcomes, Clock.fixed(NOW, ZoneOffset.UTC)).current();

        assertThat(integrity.status()).isEqualTo(ResearchIntegrityStatus.HEALTHY);
        assertThat(integrity.acquisitionByMarket().get(MARKET_ID).status())
                .isEqualTo(ResearchIntegrityStatus.HEALTHY);
        assertThat(integrity.maturationByHorizon().get("PT1H").availableCount()).isEqualTo(1);
        assertThat(integrity.overdueOutcomeCount()).isZero();
        assertThat(integrity.timestampAnomalyCount()).isZero();
    }

    @Test
    void reportsStaleAcquisitionAndOverdueMaturation() {
        ResearchLifecycleProperties properties = properties();
        FeatureSnapshotRepository snapshots = mock(FeatureSnapshotRepository.class);
        FeatureSnapshot latest = snapshot(NOW.minus(Duration.ofHours(3)));
        FeatureSnapshot candidate = snapshot(NOW.minus(Duration.ofHours(2)));
        when(snapshots.findLatestByMarket(any(), any(), anyString(), anyString()))
                .thenReturn(Optional.of(latest));
        when(snapshots.findByPopulation(any(), any(), any(), anyString(), anyString()))
                .thenReturn(List.of(candidate));
        ObservedMarketOutcomeRepository outcomes = mock(ObservedMarketOutcomeRepository.class);
        when(outcomes.findByMarketsAndCutoffRange(any(), any(), any())).thenReturn(List.of());

        ResearchIntegrity integrity = new ResearchIntegrityService(
                properties, snapshots, outcomes, Clock.fixed(NOW, ZoneOffset.UTC)).current();

        assertThat(integrity.status()).isEqualTo(ResearchIntegrityStatus.STALE);
        assertThat(integrity.overdueOutcomeCount()).isEqualTo(1);
        assertThat(integrity.maturationByHorizon().get("PT1H").overdueCount()).isEqualTo(1);
    }

    @Test
    void reportsDegradedAtExactlyOneCadenceBehind() {
        ResearchLifecycleProperties properties = properties();
        FeatureSnapshotRepository snapshots = mock(FeatureSnapshotRepository.class);
        FeatureSnapshot latestSnapshot = snapshot(NOW.minus(Duration.ofHours(2)));
        when(snapshots.findLatestByMarket(any(), any(), anyString(), anyString()))
                .thenReturn(Optional.of(latestSnapshot));
        when(snapshots.findByPopulation(any(), any(), any(), anyString(), anyString()))
                .thenReturn(List.of());
        ObservedMarketOutcomeRepository outcomes = mock(ObservedMarketOutcomeRepository.class);
        when(outcomes.findByMarketsAndCutoffRange(any(), any(), any())).thenReturn(List.of());

        ResearchIntegrity integrity = new ResearchIntegrityService(
                properties, snapshots, outcomes, Clock.fixed(NOW, ZoneOffset.UTC)).current();

        assertThat(integrity.status()).isEqualTo(ResearchIntegrityStatus.DEGRADED);
        assertThat(integrity.acquisitionByMarket().get(MARKET_ID).lagSeconds())
                .isEqualTo(Duration.ofHours(1).toSeconds());
    }

    @Test
    void reportsNoDataWhenConfiguredMarketsHaveNoSnapshots() {
        ResearchLifecycleProperties properties = properties();
        FeatureSnapshotRepository snapshots = mock(FeatureSnapshotRepository.class);
        when(snapshots.findLatestByMarket(any(), any(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(snapshots.findByPopulation(any(), any(), any(), anyString(), anyString()))
                .thenReturn(List.of());
        ObservedMarketOutcomeRepository outcomes = mock(ObservedMarketOutcomeRepository.class);
        when(outcomes.findByMarketsAndCutoffRange(any(), any(), any())).thenReturn(List.of());

        ResearchIntegrity integrity = new ResearchIntegrityService(
                properties, snapshots, outcomes, Clock.fixed(NOW, ZoneOffset.UTC)).current();

        assertThat(integrity.status()).isEqualTo(ResearchIntegrityStatus.NO_DATA_YET);
        assertThat(integrity.warnings()).contains("no_snapshot:" + MARKET_ID);
    }

    @Test
    void failsClosedWhenLatestSnapshotIsFutureDated() {
        ResearchLifecycleProperties properties = properties();
        FeatureSnapshotRepository snapshots = mock(FeatureSnapshotRepository.class);
        FeatureSnapshot futureSnapshot = snapshot(NOW.plus(Duration.ofMinutes(1)));
        when(snapshots.findLatestByMarket(any(), any(), anyString(), anyString()))
                .thenReturn(Optional.of(futureSnapshot));
        when(snapshots.findByPopulation(any(), any(), any(), anyString(), anyString()))
                .thenReturn(List.of());
        ObservedMarketOutcomeRepository outcomes = mock(ObservedMarketOutcomeRepository.class);
        when(outcomes.findByMarketsAndCutoffRange(any(), any(), any())).thenReturn(List.of());

        ResearchIntegrity integrity = new ResearchIntegrityService(
                properties, snapshots, outcomes, Clock.fixed(NOW, ZoneOffset.UTC)).current();

        assertThat(integrity.status()).isEqualTo(ResearchIntegrityStatus.UNKNOWN);
        assertThat(integrity.acquisitionByMarket().get(MARKET_ID).status())
                .isEqualTo(ResearchIntegrityStatus.UNKNOWN);
        assertThat(integrity.warnings()).contains("future_latest_snapshot:" + MARKET_ID);
    }

    @Test
    void failsClosedWhenAResearchSourceIsUnavailable() {
        ResearchLifecycleProperties properties = properties();
        FeatureSnapshotRepository snapshots = mock(FeatureSnapshotRepository.class);
        when(snapshots.findLatestByMarket(any(), any(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("database unavailable"));
        when(snapshots.findByPopulation(any(), any(), any(), anyString(), anyString()))
                .thenReturn(List.of());
        ObservedMarketOutcomeRepository outcomes = mock(ObservedMarketOutcomeRepository.class);
        when(outcomes.findByMarketsAndCutoffRange(any(), any(), any()))
                .thenThrow(new IllegalStateException("database unavailable"));

        ResearchIntegrity integrity = new ResearchIntegrityService(
                properties, snapshots, outcomes, Clock.fixed(NOW, ZoneOffset.UTC)).current();

        assertThat(integrity.status()).isEqualTo(ResearchIntegrityStatus.UNKNOWN);
        assertThat(integrity.warnings()).contains("snapshot_lookup_failed:" + MARKET_ID,
                "maturation_source_unavailable");
    }

    @Test
    void cachesTheBoundedProjection() {
        ResearchLifecycleProperties properties = properties();
        FeatureSnapshotRepository snapshots = mock(FeatureSnapshotRepository.class);
        when(snapshots.findLatestByMarket(any(), any(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(snapshots.findByPopulation(any(), any(), any(), anyString(), anyString()))
                .thenReturn(List.of());
        ObservedMarketOutcomeRepository outcomes = mock(ObservedMarketOutcomeRepository.class);
        when(outcomes.findByMarketsAndCutoffRange(any(), any(), any())).thenReturn(List.of());
        ResearchIntegrityService service = new ResearchIntegrityService(
                properties, snapshots, outcomes, Clock.fixed(NOW, ZoneOffset.UTC));

        service.current();
        service.current();

        verify(snapshots, times(1)).findLatestByMarket(
                eq(MARKET_ID), any(), eq("feature-projection-v1"), eq("STRICT_OBSERVED"));
        verify(snapshots, times(1)).findByPopulation(any(), any(), any(), anyString(), anyString());
        verify(outcomes, times(1)).findByMarketsAndCutoffRange(any(), any(), any());
        verify(outcomes, never()).findByMarkets(any());
    }

    private ResearchLifecycleProperties properties() {
        ResearchLifecycleProperties properties = new ResearchLifecycleProperties();
        properties.setEnabled(true);
        properties.setMarketIds(List.of(MARKET_ID));
        properties.setObservationCadence(Duration.ofHours(1));
        properties.setFixedDelay(Duration.ofMinutes(1));
        properties.setOutcomeHorizons(List.of(Duration.ofHours(1)));
        return properties;
    }

    private FeatureSnapshot snapshot(Instant cutoff) {
        FeatureSnapshot snapshot = mock(FeatureSnapshot.class);
        when(snapshot.marketId()).thenReturn(MARKET_ID);
        when(snapshot.evaluationCutoff()).thenReturn(cutoff);
        return snapshot;
    }

    private ObservedMarketOutcome outcome(Instant cutoff, ObservedMarketOutcomeStatus status) {
        ObservedMarketOutcome outcome = mock(ObservedMarketOutcome.class);
        when(outcome.marketId()).thenReturn(MARKET_ID);
        when(outcome.evaluationCutoff()).thenReturn(cutoff);
        when(outcome.horizon()).thenReturn(OutcomeHorizon.oneHour());
        when(outcome.maturityAt()).thenReturn(cutoff.plus(Duration.ofHours(1)));
        when(outcome.status()).thenReturn(status);
        when(outcome.computedAt()).thenReturn(NOW);
        return outcome;
    }
}
