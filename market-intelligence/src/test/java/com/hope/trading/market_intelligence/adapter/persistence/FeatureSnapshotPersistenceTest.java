package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.application.port.FeatureSnapshotRepository;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.feature.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class FeatureSnapshotPersistenceTest {
    @Autowired
    private FeatureSnapshotRepository repository;

    @Test
    void persistsTypedPayloadAndReturnsTheSameSemanticSnapshot() {
        FeatureSnapshot snapshot = snapshot("feature-snapshot-persistence");

        FeatureSnapshot first = repository.saveIfAbsent(snapshot);
        FeatureSnapshot second = repository.saveIfAbsent(snapshot);

        assertThat(second.fingerprint()).isEqualTo(first.fingerprint());
        assertThat(repository.findByFingerprint(snapshot.fingerprint())).get()
                .satisfies(value -> {
                    assertThat(value.values()).singleElement().isInstanceOf(NumericFeatureValue.class);
                    assertThat(((NumericFeatureValue) value.values().getFirst()).value())
                            .isEqualByComparingTo("0.125");
                });
    }

    @Test
    void rejectsDifferentFingerprintForTheSameSnapshotIdentity() {
        FeatureSnapshot first = snapshot("feature-snapshot-conflict");
        repository.saveIfAbsent(first);

        FeatureSnapshot conflicting = new FeatureSnapshot(
                UUID.randomUUID(), first.analysisExecutionId(), first.marketId(), first.evaluationCutoff(),
                first.evidencePolicy(), first.featureProjectionVersion(), first.contextFingerprint(),
                List.of(new NumericFeatureValue("ohlc.range_fraction", FeatureMissingness.PRESENT,
                        new BigDecimal("0.250"))), first.groups(), first.provenance(),
                "different-fingerprint", first.createdAt());

        assertThatThrownBy(() -> repository.saveIfAbsent(conflicting))
                .hasMessageContaining("identity");
    }

    @Test
    void concurrentIdenticalWritesReturnOneSemanticSnapshot() throws Exception {
        FeatureSnapshot first = snapshot("feature-snapshot-concurrent");
        FeatureSnapshot second = new FeatureSnapshot(
                UUID.randomUUID(), first.analysisExecutionId(), first.marketId(), first.evaluationCutoff(),
                first.evidencePolicy(), first.featureProjectionVersion(), first.contextFingerprint(),
                first.values(), first.groups(), first.provenance(), first.fingerprint(), first.createdAt());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<FeatureSnapshot> firstResult = executor.submit(() -> {
                start.await();
                return repository.saveIfAbsent(first);
            });
            Future<FeatureSnapshot> secondResult = executor.submit(() -> {
                start.await();
                return repository.saveIfAbsent(second);
            });
            start.countDown();

            assertThat(firstResult.get().fingerprint()).isEqualTo(secondResult.get().fingerprint());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void persistsAllSupportedTypedValuesAndMissingness() {
        FeatureSnapshot snapshot = typedSnapshot();

        repository.saveIfAbsent(snapshot);

        assertThat(repository.findByFingerprint(snapshot.fingerprint())).get()
                .satisfies(value -> {
                    assertThat(value.values()).extracting(FeatureValue::name)
                            .containsExactly("numeric", "category", "flag", "missing");
                    assertThat(value.values()).filteredOn(item -> item.name().equals("category"))
                            .first().isInstanceOfSatisfying(CategoricalFeatureValue.class,
                                    item -> assertThat(item.value()).isEqualTo("UPTREND"));
                    assertThat(value.values()).filteredOn(item -> item.name().equals("flag"))
                            .first().isInstanceOfSatisfying(BooleanFeatureValue.class,
                                    item -> assertThat(item.value()).isTrue());
                    assertThat(value.values()).filteredOn(item -> item.name().equals("numeric"))
                            .first().isInstanceOfSatisfying(NumericFeatureValue.class,
                                    item -> assertThat(item.value()).isEqualByComparingTo("0.125"));
                    assertThat(value.values()).filteredOn(item -> item.name().equals("missing"))
                            .first().extracting(FeatureValue::status)
                            .isEqualTo(FeatureMissingness.INSUFFICIENT_DATA);
                });
    }

    private FeatureSnapshot snapshot(String fingerprint) {
        return new FeatureSnapshot(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Instant.parse("2026-10-15T10:00:00Z"), EvidenceAcquisitionPolicy.STRICT_OBSERVED,
                "feature-projection-v1", "context-fingerprint",
                List.of(new NumericFeatureValue("ohlc.range_fraction", FeatureMissingness.PRESENT,
                        new BigDecimal("0.125"))),
                List.of(new FeatureGroupStatus(FeatureGroup.OHLC, FeatureMissingness.PRESENT, List.of())),
                List.of(new AnalyzerProvenance("ohlc-range-analysis", "1.1.0", "OHLC_TEMPORAL_V1",
                        null, null, null, null, null, null, "input-fingerprint", null, null, List.of(),
                         "OHLC_RANGE_ANALYSIS", "test-capability", "1.1.0", "parameters", "input-fingerprint", "v1")),
                fingerprint, Instant.parse("2026-10-15T10:01:00Z"));
    }

    private FeatureSnapshot typedSnapshot() {
        FeatureSnapshot base = snapshot("feature-snapshot-typed");
        return new FeatureSnapshot(
                base.snapshotId(), base.analysisExecutionId(), base.marketId(), base.evaluationCutoff(),
                base.evidencePolicy(), base.featureProjectionVersion(), base.contextFingerprint(),
                List.of(
                        new NumericFeatureValue("numeric", FeatureMissingness.PRESENT, new BigDecimal("0.125")),
                        new CategoricalFeatureValue("category", FeatureMissingness.PRESENT, "UPTREND"),
                        new BooleanFeatureValue("flag", FeatureMissingness.PRESENT, true),
                        new NumericFeatureValue("missing", FeatureMissingness.INSUFFICIENT_DATA, null)),
                base.groups(), base.provenance(), "feature-snapshot-typed-values", base.createdAt());
    }
}
