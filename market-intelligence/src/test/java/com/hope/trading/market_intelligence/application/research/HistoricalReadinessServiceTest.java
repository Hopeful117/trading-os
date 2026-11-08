package com.hope.trading.market_intelligence.application.research;

import com.hope.trading.market_intelligence.application.port.AnalysisExecutionRepository;
import com.hope.trading.market_intelligence.application.port.FeatureSnapshotRepository;
import com.hope.trading.market_intelligence.application.port.ObservedMarketOutcomeRepository;
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
                Set.of(MARKET), NOW.minusSeconds(3600), NOW, "profile-v1", 100), "corr-1");

        assertThat(result.verdict()).isEqualTo(HistoricalReadinessVerdict.INSUFFICIENT);
        assertThat(result.blockers()).contains("NO_FEATURE_SNAPSHOTS");
        assertThat(result.correlationId()).isEqualTo("corr-1");
    }
}
