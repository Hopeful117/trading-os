package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.replay.ReplayCostAssumptions;
import com.hope.trading.market_intelligence.domain.replay.ReplayEvidence;
import com.hope.trading.market_intelligence.domain.replay.ReplayEvidenceStatus;
import com.hope.trading.market_intelligence.domain.replay.ReplayEvidenceSample;
import com.hope.trading.market_intelligence.domain.replay.SignalReplayRequest;
import com.hope.trading.market_intelligence.domain.replay.SignalReplayResult;
import com.hope.trading.market_intelligence.strategy.domain.StrategyApplicability;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluationStatus;
import com.hope.trading.market_intelligence.strategy.domain.StrategyId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SignalReplayResultJpaPersistenceTest {
    private static final Instant CUTOFF = Instant.parse("2026-11-19T12:00:00Z");

    @Autowired
    private JpaSignalReplayResultRepository repository;

    @Test
    void immutableResultRoundTripsAndRetriesIdempotently() {
        SignalReplayResult result = SignalReplayResult.from(request(), "context-fingerprint",
                StrategyEvaluationStatus.NOT_EVALUABLE, null, List.of("INCOMPLETE_HISTORY"), CUTOFF);

        SignalReplayResult persisted = repository.saveIfAbsent(result);
        SignalReplayResult retry = repository.saveIfAbsent(result);
        SignalReplayResult reloaded = repository.findByFingerprint(result.fingerprint()).orElseThrow();

        assertThat(persisted.fingerprint()).isEqualTo(result.fingerprint());
        assertThat(retry.fingerprint()).isEqualTo(result.fingerprint());
        assertThat(reloaded.marketId()).isEqualTo(result.marketId());
        assertThat(reloaded.strategyId()).isEqualTo(result.strategyId());
        assertThat(reloaded.evidencePolicy()).isEqualTo(EvidenceAcquisitionPolicy.STRICT_OBSERVED);
        assertThat(reloaded.costAssumptions()).isEqualTo(result.costAssumptions());
    }

    @Test
    void changedEvidenceCreatesAnImmutableCorrection() {
        SignalReplayResult original = SignalReplayResult.from(request("source-v1"), "context-fingerprint",
                StrategyEvaluationStatus.NOT_EVALUABLE, null, List.of("INCOMPLETE_HISTORY"), CUTOFF);
        SignalReplayResult correction = SignalReplayResult.from(request("source-v2"), "context-fingerprint",
                StrategyEvaluationStatus.NOT_EVALUABLE, null, List.of("INCOMPLETE_HISTORY"), CUTOFF.plusSeconds(1));

        repository.saveIfAbsent(original);
        repository.saveIfAbsent(correction);

        assertThat(repository.findByFingerprint(original.fingerprint())).isPresent();
        assertThat(repository.findByFingerprint(correction.fingerprint())).isPresent();
        assertThat(original.fingerprint()).isNotEqualTo(correction.fingerprint());
    }

    @Test
    void concurrentDuplicateReplayPersistsOneFact() throws Exception {
        SignalReplayResult result = SignalReplayResult.from(request("source-concurrent"), "context-concurrent",
                StrategyEvaluationStatus.NOT_EVALUABLE, null, List.of("INCOMPLETE_HISTORY"), CUTOFF);

        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            List<Future<SignalReplayResult>> results = executor.invokeAll(
                    java.util.stream.IntStream.range(0, 8)
                            .mapToObj(ignored -> (java.util.concurrent.Callable<SignalReplayResult>)
                                    () -> repository.saveIfAbsent(result))
                            .toList());
            assertThat(results).allSatisfy(future -> {
                try {
                    assertThat(future.get().fingerprint()).isEqualTo(result.fingerprint());
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            });
        }
    }

    private SignalReplayRequest request() {
        return request("source-fingerprint");
    }

    private SignalReplayRequest request(String sourceFingerprint) {
        return new SignalReplayRequest(
                UUID.fromString("66666666-6666-6666-6666-666666666666"),
                "CFD", "EURUSD", "provider", StrategyApplicability.Timeframe.M15,
                CUTOFF, new StrategyId(UUID.fromString("77777777-7777-7777-7777-777777777777")), 1,
                EvidenceAcquisitionPolicy.STRICT_OBSERVED,
                new ReplayEvidence(ReplayEvidenceStatus.INCOMPLETE, "provider", sourceFingerprint,
                        List.of(sample()), List.of("INCOMPLETE_HISTORY")),
                new ReplayCostAssumptions("cost-v1", new BigDecimal("0.0001"), new BigDecimal("0.00002")));
    }

    private ReplayEvidenceSample sample() {
        return new ReplayEvidenceSample(UUID.randomUUID(), "provider", "source", "historical-ohlc-v1",
                CUTOFF.minusSeconds(900), CUTOFF.minusSeconds(600), CUTOFF.minusSeconds(500),
                CUTOFF.minusSeconds(400), CUTOFF.minusSeconds(1), null, true, false, "evidence");
    }
}
