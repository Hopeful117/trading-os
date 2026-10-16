package com.hope.trading.market_intelligence.domain.execution;

import com.hope.trading.market_intelligence.application.capability.ProductionArtifactTypes;
import com.hope.trading.market_intelligence.application.port.*;
import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.MarketSnapshotContext;
import com.hope.trading.market_intelligence.domain.artifact.*;
import com.hope.trading.market_intelligence.domain.capability.*;
import com.hope.trading.market_intelligence.domain.context.ContextClassification;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DurableExecutionPersistenceTest {
    @Autowired AnalysisExecutionRepository analyses;
    @Autowired CapabilityExecutionRepository capabilities;
    @Autowired ArtifactPersistencePort artifacts;
    private final Instant now = Instant.parse("2026-08-01T12:00:00Z");

    @Test
    void restartFacingRepositoriesRehydrateAnalysisCapabilityResultAndArtifact() {
        AnalysisExecution running = ExecutionTestFixtures.requested(now)
                .transitionTo(AnalysisExecutionStatus.ACCEPTED, now.plusSeconds(1))
                .transitionTo(AnalysisExecutionStatus.CONTEXT_BUILDING, now.plusSeconds(2))
                .transitionTo(AnalysisExecutionStatus.RUNNING, now.plusSeconds(3));
        AnalysisExecution completed = running.complete(
                ExecutionTestFixtures.result(
                        running.executionId(), running.provenance().marketId(), now),
                AnalysisResultQuality.COMPLETE, now.plusSeconds(4));
        analyses.save(completed);

        StoredArtifact stored = artifact(running.provenance().marketId());
        ProducedArtifact produced = new ProducedArtifact(
                ProductionArtifactTypes.MARKET_SNAPSHOT,
                ProductionArtifactTypes.V1, stored);
        artifacts.save(running.executionId(), produced);
        CapabilityMetadata metadata = new CapabilityMetadata(
                new CapabilityId("test-capability"), new CapabilityVersion("1.0.0"),
                CapabilityCategory.DETERMINISTIC, ExecutionPolicy.REQUIRED,
                com.hope.trading.market_intelligence.domain.capability.RetryPolicy.disabled(),
                List.of(), List.of(new ProducedContribution.ArtifactContribution(
                ProductionArtifactTypes.MARKET_SNAPSHOT,
                ProductionArtifactTypes.V1, Set.of())), Duration.ofSeconds(1), null);
        CapabilityExecution execution = CapabilityExecution.created(
                        running.executionId(), metadata, now)
                .transitionTo(CapabilityExecutionState.READY, now)
                .transitionTo(CapabilityExecutionState.RUNNING, now)
                .complete(new CapabilityResult(
                        metadata.producedContributions(), List.of(produced),
                        Map.of("price", new BigDecimal("100")), List.of(),
                        CapabilityCompleteness.COMPLETE), now.plusSeconds(1));
        capabilities.save(execution);

        assertThat(analyses.findById(running.executionId())).get()
                .extracting(AnalysisExecution::status)
                .isEqualTo(AnalysisExecutionStatus.COMPLETED);
        assertThat(artifacts.find(running.executionId(),
                ProductionArtifactTypes.MARKET_SNAPSHOT, ProductionArtifactTypes.V1))
                .singleElement().satisfies(value -> assertThat(value.artifact().content())
                        .isInstanceOf(MarketSnapshotContext.class));
        assertThat(capabilities.findByAnalysisExecutionId(running.executionId()))
                .singleElement().satisfies(value -> {
                    assertThat(value.state()).isEqualTo(CapabilityExecutionState.COMPLETED);
                    assertThat(value.result()).get().extracting(CapabilityResult::completeness)
                            .isEqualTo(CapabilityCompleteness.COMPLETE);
                 });
    }

    @Test
    void rehydratesStrictResearchScopeAfterPersistenceRestartBoundary() {
        UUID marketId = UUID.randomUUID();
        Instant cutoff = Instant.parse("2026-08-01T10:00:00Z");
        AnalysisExecution requested = AnalysisExecution.requested(
                UUID.randomUUID(), new IdempotencyKey("research-restart-" + UUID.randomUUID()),
                ExecutionTestFixtures.policy(), cutoff,
                List.of("spread-analysis"),
                new AnalysisExecutionProvenance(
                        marketId, AnalysisExecutionMode.PASSIVE, "research-feature-snapshot-v1", "v1",
                        null, null, null, cutoff, EvidenceAcquisitionPolicy.STRICT_OBSERVED),
                AnalysisTraceMetadata.empty());
        AnalysisExecution completed = requested
                .transitionTo(AnalysisExecutionStatus.ACCEPTED, cutoff)
                .transitionTo(AnalysisExecutionStatus.CONTEXT_BUILDING, cutoff)
                .transitionTo(AnalysisExecutionStatus.RUNNING, cutoff)
                .complete(
                ExecutionTestFixtures.result(requested.executionId(), marketId, cutoff),
                AnalysisResultQuality.COMPLETE, cutoff.plusSeconds(1));

        analyses.save(completed);

        assertThat(analyses.findByResearchScope(
                List.of(marketId), cutoff.minusSeconds(1), cutoff.plusSeconds(1),
                EvidenceAcquisitionPolicy.STRICT_OBSERVED))
                .singleElement()
                .satisfies(value -> {
                    assertThat(value.executionId()).isEqualTo(completed.executionId());
                    assertThat(value.provenance().evaluationCutoff()).isEqualTo(cutoff);
                    assertThat(value.provenance().evidencePolicy())
                            .isEqualTo(EvidenceAcquisitionPolicy.STRICT_OBSERVED);
                    assertThat(value.result()).isPresent();
                });
    }

    @Test
    void concurrentSameIdempotencyExecutionInsertReturnsDurableExistingExecution() throws Exception {
        Instant cutoff = Instant.parse("2026-08-01T10:00:00Z");
        String key = "research-concurrent-" + UUID.randomUUID();
        AnalysisExecution first = strictRequested(key, cutoff);
        AnalysisExecution second = strictRequested(key, cutoff);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<AnalysisExecution> firstResult = executor.submit(() -> saveAfterStart(first, start));
            Future<AnalysisExecution> secondResult = executor.submit(() -> saveAfterStart(second, start));
            start.countDown();

            assertThat(firstResult.get().idempotencyKey().value()).isEqualTo(key);
            assertThat(secondResult.get().idempotencyKey().value()).isEqualTo(key);
            assertThat(analyses.findByIdempotencyKey(new IdempotencyKey(key))).isPresent();
        } finally {
            executor.shutdownNow();
        }
    }

    private AnalysisExecution saveAfterStart(AnalysisExecution execution, CountDownLatch start)
            throws InterruptedException {
        start.await();
        return analyses.save(execution);
    }

    private AnalysisExecution strictRequested(String idempotencyKey, Instant cutoff) {
        UUID marketId = UUID.randomUUID();
        return AnalysisExecution.requested(
                UUID.randomUUID(), new IdempotencyKey(idempotencyKey), ExecutionTestFixtures.policy(), cutoff,
                List.of("spread-analysis"),
                new AnalysisExecutionProvenance(
                        marketId, AnalysisExecutionMode.PASSIVE, "research-feature-snapshot-v1", "v1",
                        null, null, null, cutoff, EvidenceAcquisitionPolicy.STRICT_OBSERVED),
                AnalysisTraceMetadata.empty());
    }

    private StoredArtifact artifact(UUID marketId) {
        return new StoredArtifact(
                new ArtifactCacheKey(
                        new ArtifactIdentity("normalized-market-snapshot", "market-data", "v1"),
                        new ArtifactScope(marketId, "BTC/USD", null, null, null, null,
                                AnalysisExecutionMode.ACTIVE, ContextClassification.PUBLIC),
                        ArtifactFingerprint.empty(), ArtifactFingerprint.ofInputs(List.of("ticker-1"))),
                new MarketSnapshotContext(marketId, "BTC/USD", new BigDecimal("100"),
                        new BigDecimal("99"), new BigDecimal("101"), true, now),
                ArtifactFreshness.validUntil(now, now.plusSeconds(30), "ticker-1"),
                new ArtifactProvenance("market-data", "v1", null, now, Set.of(), Set.of()),
                AnalysisResultQuality.COMPLETE);
    }
}
