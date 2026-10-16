package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.application.port.ObservedMarketOutcomeRepository;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcome;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcomeStatus;
import com.hope.trading.market_intelligence.domain.outcome.OutcomeHorizon;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ObservedMarketOutcomePersistenceTest {
    @Autowired
    private ObservedMarketOutcomeRepository repository;

    @Test
    void persistsExactMetricsAndReturnsSameArtifactForIdenticalFingerprint() {
        UUID marketId = UUID.randomUUID();
        ObservedMarketOutcome outcome = available(marketId, "outcome-fingerprint-a", "future-fingerprint-a");

        ObservedMarketOutcome first = repository.saveIfAbsent(outcome);
        ObservedMarketOutcome second = repository.saveIfAbsent(outcome);

        assertThat(second.outcomeFingerprint()).isEqualTo(first.outcomeFingerprint());
        assertThat(second.forwardReturn()).isEqualByComparingTo("0.02");
        assertThat(repository.findByFingerprint(outcome.outcomeFingerprint())).get()
                .extracting(ObservedMarketOutcome::outcomeFingerprint,
                        ObservedMarketOutcome::futureEvidenceFingerprint)
                .containsExactly("outcome-fingerprint-a", "future-fingerprint-a");
        assertThat(repository.findByMarketAndCutoffAndHorizon(
                marketId, outcome.evaluationCutoff(), outcome.horizon().value().toSeconds()))
                .hasSize(1);
    }

    @Test
    void preservesNewEvidenceVersionInsteadOfOverwritingPreviousOutcome() {
        UUID marketId = UUID.randomUUID();
        ObservedMarketOutcome first = available(marketId, "outcome-fingerprint-b1", "future-fingerprint-b1");
        ObservedMarketOutcome corrected = available(marketId, "outcome-fingerprint-b2", "future-fingerprint-b2");

        repository.saveIfAbsent(first);
        repository.saveIfAbsent(corrected);

        assertThat(repository.findByMarketAndCutoffAndHorizon(
                marketId, first.evaluationCutoff(), first.horizon().value().toSeconds()))
                .extracting(ObservedMarketOutcome::futureEvidenceFingerprint)
                .containsExactlyInAnyOrder("future-fingerprint-b1", "future-fingerprint-b2");
    }

    @Test
    void concurrentIdenticalInsertReturnsOneSemanticArtifact() throws Exception {
        UUID marketId = UUID.randomUUID();
        ObservedMarketOutcome outcome = available(marketId, "outcome-fingerprint-c", "future-fingerprint-c");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> saveAfterStart(outcome, ready, start));
            var second = executor.submit(() -> saveAfterStart(outcome, ready, start));
            ready.await();
            start.countDown();

            assertThat(first.get().outcomeFingerprint()).isEqualTo(second.get().outcomeFingerprint());
            assertThat(second.get().forwardReturn()).isEqualByComparingTo(first.get().forwardReturn());
            assertThat(repository.findByFingerprint(outcome.outcomeFingerprint())).isPresent();
            assertThat(repository.findByMarketAndCutoffAndHorizon(
                    marketId, outcome.evaluationCutoff(), outcome.horizon().value().toSeconds()))
                    .hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private ObservedMarketOutcome saveAfterStart(
            ObservedMarketOutcome outcome, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        return repository.saveIfAbsent(outcome);
    }

    private ObservedMarketOutcome available(UUID marketId, String fingerprint, String futureFingerprint) {
        Instant cutoff = Instant.parse("2026-10-15T10:00:00Z");
        return new ObservedMarketOutcome(
                marketId, cutoff, OutcomeHorizon.oneHour(), cutoff.plusSeconds(3600),
                ObservedMarketOutcomeStatus.AVAILABLE, null,
                new BigDecimal("100"), "reference-fingerprint",
                new BigDecimal("102"), new BigDecimal("105"), new BigDecimal("98"),
                new BigDecimal("0.02"), new BigDecimal("0.05"), new BigDecimal("-0.02"),
                 new BigDecimal("0.07"), 4, 4,
                 "historical-ohlc-v1", java.util.List.of("historical-ohlc-v1"),
                 futureFingerprint,
                "market-outcome-v1", fingerprint, cutoff.plusSeconds(3600), null);
    }
}
