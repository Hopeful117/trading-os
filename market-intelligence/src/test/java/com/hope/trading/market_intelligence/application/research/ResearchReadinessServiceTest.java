package com.hope.trading.market_intelligence.application.research;

import com.hope.trading.market_intelligence.adapter.marketdata.HistoricalOhlcClient;
import com.hope.trading.market_intelligence.adapter.marketdata.OrderBookReplayClient;
import com.hope.trading.market_intelligence.application.port.ArtifactPersistencePort;
import com.hope.trading.market_intelligence.application.port.FeatureSnapshotRepository;
import com.hope.trading.market_intelligence.application.port.ObservedMarketOutcomeRepository;
import com.hope.trading.market_intelligence.config.ResearchLifecycleProperties;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcome;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcomeStatus;
import com.hope.trading.market_intelligence.domain.outcome.OutcomeHorizon;
import com.hope.trading.market_intelligence.domain.feature.FeatureSnapshot;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

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
    void readinessCachesTheExpensiveProjectionWithinConfiguredFreshness() {
        ResearchLifecycleProperties properties = new ResearchLifecycleProperties();
        properties.setEnabled(true);
        properties.setMarketIds(List.of(MARKET_ID));
        properties.setOutcomeHorizons(List.of(Duration.ofHours(1)));
        properties.setReadinessCacheDuration(Duration.ofMinutes(1));

        FeatureSnapshotRepository snapshots = mock(FeatureSnapshotRepository.class);
        when(snapshots.findByPopulation(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(List.of());
        ObservedMarketOutcomeRepository outcomes = mock(ObservedMarketOutcomeRepository.class);
        when(outcomes.findByMarkets(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        var service = new ResearchReadinessService(
                properties, snapshots, outcomes, mock(HistoricalOhlcClient.class),
                Clock.fixed(NOW, ZoneOffset.UTC));

        service.current();
        service.current();

        verify(snapshots, times(1)).findByPopulation(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verify(outcomes, times(1)).findByMarkets(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void readinessRecomputesAfterTheConfiguredFreshnessWindow() {
        ResearchLifecycleProperties properties = new ResearchLifecycleProperties();
        properties.setEnabled(true);
        properties.setMarketIds(List.of(MARKET_ID));
        properties.setOutcomeHorizons(List.of(Duration.ofHours(1)));
        properties.setReadinessCacheDuration(Duration.ofMinutes(1));

        FeatureSnapshotRepository snapshots = mock(FeatureSnapshotRepository.class);
        when(snapshots.findByPopulation(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(List.of());
        ObservedMarketOutcomeRepository outcomes = mock(ObservedMarketOutcomeRepository.class);
        when(outcomes.findByMarkets(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        MutableClock clock = new MutableClock(NOW);
        var service = new ResearchReadinessService(
                properties, snapshots, outcomes, mock(HistoricalOhlcClient.class), clock);

        service.current();
        clock.advance(Duration.ofMinutes(1).plusSeconds(1));
        service.current();

        verify(snapshots, times(2)).findByPopulation(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void concurrentReadinessRequestsShareOneProjectionComputation() throws Exception {
        ResearchLifecycleProperties properties = new ResearchLifecycleProperties();
        properties.setEnabled(true);
        properties.setMarketIds(List.of(MARKET_ID));
        properties.setOutcomeHorizons(List.of(Duration.ofHours(1)));

        CountDownLatch computationStarted = new CountDownLatch(1);
        CountDownLatch releaseComputation = new CountDownLatch(1);
        FeatureSnapshotRepository snapshots = mock(FeatureSnapshotRepository.class);
        when(snapshots.findByPopulation(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> {
                    computationStarted.countDown();
                    releaseComputation.await(5, TimeUnit.SECONDS);
                    return List.of();
                });
        ObservedMarketOutcomeRepository outcomes = mock(ObservedMarketOutcomeRepository.class);
        when(outcomes.findByMarkets(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        var service = new ResearchReadinessService(
                properties, snapshots, outcomes, mock(HistoricalOhlcClient.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(service::current);
            assertThat(computationStarted.await(5, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(service::current);
            releaseComputation.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            releaseComputation.countDown();
            executor.shutdownNow();
        }

        verify(snapshots, times(1)).findByPopulation(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
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

    @Test
    void s1ReadinessFailsClosedWhenFeeIdentityIsNotConfigured() {
        ResearchLifecycleProperties properties = new ResearchLifecycleProperties();
        properties.setEnabled(true);
        properties.setMarketIds(List.of(MARKET_ID));
        properties.setOutcomeHorizons(List.of(Duration.ofHours(1)));

        FeatureSnapshot signal = mock(FeatureSnapshot.class);
        when(signal.marketId()).thenReturn(MARKET_ID);
        when(signal.evaluationCutoff()).thenReturn(NOW.minus(Duration.ofDays(15)));
        when(signal.values()).thenReturn(List.of(new com.hope.trading.market_intelligence.domain.feature.CategoricalFeatureValue(
                "trend.trigger.break_status",
                com.hope.trading.market_intelligence.domain.feature.FeatureMissingness.PRESENT,
                "CONFIRMED_BULLISH_BREAK")));
        FeatureSnapshotRepository snapshots = mock(FeatureSnapshotRepository.class);
        when(snapshots.findByPopulation(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(List.of(signal));
        ObservedMarketOutcomeRepository outcomes = mock(ObservedMarketOutcomeRepository.class);
        when(outcomes.findByMarkets(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        ResearchReadiness readiness = new ResearchReadinessService(
                properties, snapshots, outcomes, mock(HistoricalOhlcClient.class),
                mock(ArtifactPersistencePort.class), mock(OrderBookReplayClient.class),
                mock(com.hope.trading.market_intelligence.adapter.broker.HistoricalFeeScheduleClient.class),
                Clock.fixed(NOW, ZoneOffset.UTC)).current();

        assertThat(readiness.s1().totalSignals()).isEqualTo(1);
        assertThat(readiness.s1().feeCoverage()).isZero();
        assertThat(readiness.s1().ready()).isFalse();
        assertThat(readiness.s1().blockingReasons()).contains("fee_coverage_incomplete");
    }

    @Test
    void s1ReadinessChecksReplayAndFeesAtT0AndT1() {
        ResearchLifecycleProperties properties = new ResearchLifecycleProperties();
        properties.setEnabled(true);
        properties.setMarketIds(List.of(MARKET_ID));
        properties.setOutcomeHorizons(List.of(Duration.ofHours(1)));
        var feeConfig = properties.getFeeCoverage();
        feeConfig.setBrokerAccountId(UUID.randomUUID());
        feeConfig.setProvider("KRAKEN");
        feeConfig.setProductType("LINEAR_PERPETUAL");
        feeConfig.setInstrument("PF_XBTUSD");

        Instant cutoff = NOW.minus(Duration.ofDays(15));
        FeatureSnapshot signal = mock(FeatureSnapshot.class);
        when(signal.marketId()).thenReturn(MARKET_ID);
        when(signal.evaluationCutoff()).thenReturn(cutoff);
        when(signal.values()).thenReturn(List.of(new com.hope.trading.market_intelligence.domain.feature.CategoricalFeatureValue(
                "trend.trigger.break_status",
                com.hope.trading.market_intelligence.domain.feature.FeatureMissingness.PRESENT,
                "CONFIRMED_BEARISH_BREAK")));
        FeatureSnapshotRepository snapshots = mock(FeatureSnapshotRepository.class);
        when(snapshots.findByPopulation(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(List.of(signal, signal));
        ObservedMarketOutcomeRepository outcomes = mock(ObservedMarketOutcomeRepository.class);
        when(outcomes.findByMarkets(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        OrderBookReplayClient replay = mock(OrderBookReplayClient.class);
        var replayResponse = mock(OrderBookReplayClient.ReplayResponse.class);
        when(replayResponse.status()).thenReturn("AVAILABLE");
        when(replay.replay(MARKET_ID, 25, cutoff)).thenReturn(replayResponse);
        when(replay.replay(MARKET_ID, 25, cutoff.plus(Duration.ofHours(1)))).thenReturn(replayResponse);
        var fees = mock(com.hope.trading.market_intelligence.adapter.broker.HistoricalFeeScheduleClient.class);
        when(fees.findEligible(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(NOW)))
                .thenReturn(ResponseEntity.ok(mock(
                        com.hope.trading.market_intelligence.adapter.broker.HistoricalFeeScheduleClient.ScheduleResponse.class)));

        ResearchReadiness readiness = new ResearchReadinessService(
                properties, snapshots, outcomes, mock(HistoricalOhlcClient.class),
                mock(ArtifactPersistencePort.class), replay, fees,
                Clock.fixed(NOW, ZoneOffset.UTC)).current();

        assertThat(readiness.s1().shortSignals()).isEqualTo(2);
        assertThat(readiness.s1().replayCoverage()).isEqualTo(1.0d);
        assertThat(readiness.s1().feeCoverage()).isEqualTo(1.0d);
        verify(replay).replay(MARKET_ID, 25, cutoff);
        verify(replay).replay(MARKET_ID, 25, cutoff.plus(Duration.ofHours(1)));
        verify(fees).findEligible(feeConfig.getBrokerAccountId(), "KRAKEN", "LINEAR_PERPETUAL",
                "PF_XBTUSD", cutoff, NOW);
        verify(fees).findEligible(feeConfig.getBrokerAccountId(), "KRAKEN", "LINEAR_PERPETUAL",
                "PF_XBTUSD", cutoff.plus(Duration.ofHours(1)), NOW);
    }

    @Test
    void s1ReadinessKeepsCheckingAfterHistoricalFeeScheduleNotFound() {
        ResearchLifecycleProperties properties = new ResearchLifecycleProperties();
        properties.setEnabled(true);
        properties.setMarketIds(List.of(MARKET_ID));
        properties.setOutcomeHorizons(List.of(Duration.ofHours(1)));
        var feeConfig = properties.getFeeCoverage();
        feeConfig.setBrokerAccountId(UUID.randomUUID());
        feeConfig.setProvider("KRAKEN");
        feeConfig.setProductType("LINEAR_PERPETUAL");
        feeConfig.setInstrument("PF_XBTUSD");

        Instant cutoff = NOW.minus(Duration.ofDays(15));
        FeatureSnapshot signal = mock(FeatureSnapshot.class);
        when(signal.marketId()).thenReturn(MARKET_ID);
        when(signal.evaluationCutoff()).thenReturn(cutoff);
        when(signal.values()).thenReturn(List.of(new com.hope.trading.market_intelligence.domain.feature.CategoricalFeatureValue(
                "trend.trigger.break_status",
                com.hope.trading.market_intelligence.domain.feature.FeatureMissingness.PRESENT,
                "CONFIRMED_BEARISH_BREAK")));
        FeatureSnapshotRepository snapshots = mock(FeatureSnapshotRepository.class);
        when(snapshots.findByPopulation(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(List.of(signal));
        ObservedMarketOutcomeRepository outcomes = mock(ObservedMarketOutcomeRepository.class);
        when(outcomes.findByMarkets(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        var fees = mock(com.hope.trading.market_intelligence.adapter.broker.HistoricalFeeScheduleClient.class);
        when(fees.findEligible(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(cutoff), org.mockito.ArgumentMatchers.eq(NOW)))
                .thenThrow(FeignException.errorStatus("HistoricalFeeScheduleClient#findEligible",
                        Response.builder().status(404).request(Request.create(Request.HttpMethod.GET,
                                "/internal/v1/historical-fees/eligible", java.util.Map.of(), null, null, null)).build()));
        when(fees.findEligible(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(cutoff.plus(Duration.ofHours(1))), org.mockito.ArgumentMatchers.eq(NOW)))
                .thenReturn(ResponseEntity.ok(mock(
                        com.hope.trading.market_intelligence.adapter.broker.HistoricalFeeScheduleClient.ScheduleResponse.class)));

        ResearchReadiness readiness = new ResearchReadinessService(
                properties, snapshots, outcomes, mock(HistoricalOhlcClient.class),
                mock(ArtifactPersistencePort.class), mock(OrderBookReplayClient.class), fees,
                Clock.fixed(NOW, ZoneOffset.UTC)).current();

        assertThat(readiness.s1().feeCoverage()).isEqualTo(0.5d);
        assertThat(readiness.s1().blockingReasons()).contains("fee_coverage_incomplete");
    }

    @Test
    void s1ReadinessDoesNotClassifyUnconfirmedBreakAsSignal() {
        ResearchLifecycleProperties properties = new ResearchLifecycleProperties();
        properties.setEnabled(true);
        properties.setMarketIds(List.of(MARKET_ID));
        properties.setOutcomeHorizons(List.of(Duration.ofHours(1)));
        FeatureSnapshot snapshot = mock(FeatureSnapshot.class);
        when(snapshot.marketId()).thenReturn(MARKET_ID);
        when(snapshot.evaluationCutoff()).thenReturn(NOW.minus(Duration.ofDays(15)));
        when(snapshot.values()).thenReturn(List.of(new com.hope.trading.market_intelligence.domain.feature.CategoricalFeatureValue(
                "trend.trigger.break_status",
                com.hope.trading.market_intelligence.domain.feature.FeatureMissingness.PRESENT,
                "UNCONFIRMED_BULLISH_BREAK")));
        FeatureSnapshotRepository snapshots = mock(FeatureSnapshotRepository.class);
        when(snapshots.findByPopulation(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(List.of(snapshot));
        ObservedMarketOutcomeRepository outcomes = mock(ObservedMarketOutcomeRepository.class);
        when(outcomes.findByMarkets(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        ResearchReadiness readiness = new ResearchReadinessService(
                properties, snapshots, outcomes, mock(HistoricalOhlcClient.class),
                Clock.fixed(NOW, ZoneOffset.UTC)).current();

        assertThat(readiness.s1().totalSignals()).isZero();
        assertThat(readiness.s1().blockingReasons()).contains("no_signal_period");
    }

    @Test
    void s1ReadinessFailsClosedWhenRemoteEvidenceBudgetIsExceeded() {
        ResearchLifecycleProperties properties = new ResearchLifecycleProperties();
        properties.setEnabled(true);
        properties.setMarketIds(List.of(MARKET_ID));
        properties.setOutcomeHorizons(List.of(Duration.ofHours(1)));
        List<FeatureSnapshot> snapshotsData = new java.util.ArrayList<>();
        for (int index = 0; index < 201; index++) {
            FeatureSnapshot snapshot = mock(FeatureSnapshot.class);
            when(snapshot.marketId()).thenReturn(MARKET_ID);
            when(snapshot.evaluationCutoff()).thenReturn(NOW.minus(Duration.ofDays(15)).plus(Duration.ofHours(index * 3L)));
            when(snapshot.values()).thenReturn(List.of(new com.hope.trading.market_intelligence.domain.feature.CategoricalFeatureValue(
                    "trend.trigger.break_status",
                    com.hope.trading.market_intelligence.domain.feature.FeatureMissingness.PRESENT,
                    "CONFIRMED_BULLISH_BREAK")));
            snapshotsData.add(snapshot);
        }
        FeatureSnapshotRepository snapshots = mock(FeatureSnapshotRepository.class);
        when(snapshots.findByPopulation(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(snapshotsData);
        ObservedMarketOutcomeRepository outcomes = mock(ObservedMarketOutcomeRepository.class);
        when(outcomes.findByMarkets(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        OrderBookReplayClient replay = mock(OrderBookReplayClient.class);
        var replayResponse = mock(OrderBookReplayClient.ReplayResponse.class);
        when(replayResponse.status()).thenReturn("AVAILABLE");
        when(replay.replay(org.mockito.ArgumentMatchers.eq(MARKET_ID), org.mockito.ArgumentMatchers.eq(25),
                org.mockito.ArgumentMatchers.any())).thenReturn(replayResponse);

        ResearchReadiness readiness = new ResearchReadinessService(
                properties, snapshots, outcomes, mock(HistoricalOhlcClient.class),
                mock(ArtifactPersistencePort.class), replay, null,
                Clock.fixed(NOW, ZoneOffset.UTC)).current();

        assertThat(readiness.s1().replayAvailable()).isEqualTo(400);
        assertThat(readiness.s1().blockingReasons()).contains("remote_evidence_check_budget_exceeded");
        verify(replay, times(400)).replay(org.mockito.ArgumentMatchers.eq(MARKET_ID),
                org.mockito.ArgumentMatchers.eq(25), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void s1ReadinessStopsRemoteReplayChecksAfterDependencyFailure() {
        ResearchLifecycleProperties properties = new ResearchLifecycleProperties();
        properties.setEnabled(true);
        properties.setMarketIds(List.of(MARKET_ID));
        properties.setOutcomeHorizons(List.of(Duration.ofHours(1)));

        FeatureSnapshot first = mock(FeatureSnapshot.class);
        when(first.marketId()).thenReturn(MARKET_ID);
        when(first.evaluationCutoff()).thenReturn(NOW.minus(Duration.ofDays(15)));
        when(first.values()).thenReturn(List.of(new com.hope.trading.market_intelligence.domain.feature.CategoricalFeatureValue(
                "trend.trigger.break_status",
                com.hope.trading.market_intelligence.domain.feature.FeatureMissingness.PRESENT,
                "CONFIRMED_BULLISH_BREAK")));
        FeatureSnapshot second = mock(FeatureSnapshot.class);
        when(second.marketId()).thenReturn(MARKET_ID);
        when(second.evaluationCutoff()).thenReturn(NOW.minus(Duration.ofDays(14)));
        List<com.hope.trading.market_intelligence.domain.feature.FeatureValue> signalValues =
                List.of(new com.hope.trading.market_intelligence.domain.feature.CategoricalFeatureValue(
                        "trend.trigger.break_status",
                        com.hope.trading.market_intelligence.domain.feature.FeatureMissingness.PRESENT,
                        "CONFIRMED_BULLISH_BREAK"));
        when(second.values()).thenReturn(signalValues);

        FeatureSnapshotRepository snapshots = mock(FeatureSnapshotRepository.class);
        when(snapshots.findByPopulation(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(List.of(first, second));
        ObservedMarketOutcomeRepository outcomes = mock(ObservedMarketOutcomeRepository.class);
        when(outcomes.findByMarkets(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        OrderBookReplayClient replay = mock(OrderBookReplayClient.class);
        when(replay.replay(org.mockito.ArgumentMatchers.eq(MARKET_ID), org.mockito.ArgumentMatchers.eq(25),
                org.mockito.ArgumentMatchers.any())).thenThrow(new IllegalStateException("unavailable"));

        ResearchReadiness readiness = new ResearchReadinessService(
                properties, snapshots, outcomes, mock(HistoricalOhlcClient.class),
                mock(ArtifactPersistencePort.class), replay, null,
                Clock.fixed(NOW, ZoneOffset.UTC)).current();

        assertThat(readiness.s1().replayAvailable()).isZero();
        verify(replay).replay(org.mockito.ArgumentMatchers.eq(MARKET_ID),
                org.mockito.ArgumentMatchers.eq(25), org.mockito.ArgumentMatchers.any());
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

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
