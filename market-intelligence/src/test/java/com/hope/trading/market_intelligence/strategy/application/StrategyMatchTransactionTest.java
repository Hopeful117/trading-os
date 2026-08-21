package com.hope.trading.market_intelligence.strategy.application;

import com.hope.trading.market_intelligence.strategy.domain.ConditionResult;
import com.hope.trading.market_intelligence.strategy.domain.MatchedDirection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 0011 mandatory transaction-safety proofs (shadow model):
 *
 * A. outer rollback  -> no StrategyMatch
 * B. outer commit    -> afterCommit persists the match
 * C. recorder failure after commit -> legacy truth unaffected
 * D. retry           -> idempotent, one logical row
 * E. persistence never happens before T1 commit
 */
@SpringBootTest
@ActiveProfiles("test")
class StrategyMatchTransactionTest {

    private static final Instant MATCHED = Instant.parse("2026-08-21T11:00:00Z");
    private static final UUID STRATEGY = UUID.fromString("0a10c7e2-9d1e-4f5a-b6c8-123456789001");

    @Autowired StrategyMatchRecorder recorder;
    @Autowired com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository repository;
    @Autowired PlatformTransactionManager transactions;

    private PendingStrategyMatchRecord pending(UUID analysis, String digest) {
        return new PendingStrategyMatchRecord(STRATEGY, 1,
                UUID.fromString("cccccccc-1111-2222-3333-444444444444"),
                analysis, UUID.randomUUID(), MatchedDirection.SHORT, digest,
                List.of(new ConditionResult("directional_price_change", true,
                        new BigDecimal("-3.5").toPlainString())),
                MATCHED);
    }

    private TransactionTemplate template() {
        return new TransactionTemplate(transactions);
    }

    /** Failing persister simulating a T2 outage after T1 commit. */
    private StrategyMatchRecorder failingRecorder(AtomicInteger attempts) {
        StrategyMatchPersister failing = new StrategyMatchPersister(
                new com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository() {
                    @Override
                    public Optional<com.hope.trading.market_intelligence.strategy.domain.StrategyMatch> findById(UUID id) {
                        throw new IllegalStateException("db down");
                    }

                    @Override
                    public Optional<com.hope.trading.market_intelligence.strategy.domain.StrategyMatch> findByIdentity(
                            com.hope.trading.market_intelligence.strategy.domain.StrategyMatchIdentity identity) {
                        return Optional.empty();
                    }

                    @Override
                    public List<com.hope.trading.market_intelligence.strategy.domain.StrategyMatch> findByAnalysisExecutionId(UUID id) {
                        throw new IllegalStateException("db down");
                    }

                    @Override
                    public com.hope.trading.market_intelligence.strategy.domain.StrategyMatch save(
                            com.hope.trading.market_intelligence.strategy.domain.StrategyMatch match) {
                        attempts.incrementAndGet();
                        throw new IllegalStateException("db down");
                    }
                },
                java.time.Clock.systemUTC());
        return new StrategyMatchRecorder(failing);
    }

    @Test
    void rollbackPreventsStrategyMatchPersistence() {
        UUID analysis = UUID.randomUUID();
        AtomicInteger persistedAfterRollback = new AtomicInteger();
        TransactionTemplate tx = template();
        tx.executeWithoutResult(status -> {
            recorder.recordAfterCommit(evaluationFor(pending(analysis, "tx-rollback")),
                    analysis, UUID.randomUUID());
            persistedAfterRollback.set(repository.findByAnalysisExecutionId(analysis).size());
            status.setRollbackOnly();
        });
        // nothing persisted before OR after rollback; afterCommit never ran
        assertThat(persistedAfterRollback.get()).isZero();
        assertThat(repository.findByAnalysisExecutionId(analysis)).isEmpty();
    }

    @Test
    void commitTriggersAfterCommitPersistence() {
        UUID analysis = UUID.randomUUID();
        TransactionTemplate tx = template();
        tx.executeWithoutResult(status -> recorder.recordAfterCommit(
                evaluationFor(pending(analysis, "tx-commit")),
                analysis, UUID.randomUUID()));
        List<?> matches = repository.findByAnalysisExecutionId(analysis);
        assertThat(matches).hasSize(1); // only possible if afterCommit ran post-commit
    }

    @Test
    void persistenceNeverHappensBeforeCommit() {
        UUID analysis = UUID.randomUUID();
        TransactionTemplate tx = template();
        tx.executeWithoutResult(status -> {
            recorder.recordAfterCommit(evaluationFor(pending(analysis, "tx-before")),
                    analysis, UUID.randomUUID());
            // still inside T1: no row may exist yet
            assertThat(repository.findByAnalysisExecutionId(analysis)).isEmpty();
        });
    }

    @Test
    void recorderFailureAfterCommitIsBoundedAndDoesNotThrow() {
        UUID analysis = UUID.randomUUID();
        AtomicInteger attempts = new AtomicInteger();
        StrategyMatchRecorder failing = failingRecorder(attempts);
        TransactionTemplate tx = template();
        // must not throw out of afterCommit even though the "database" is down
        tx.executeWithoutResult(status -> failing.recordAfterCommit(
                evaluationFor(pending(analysis, "tx-fail")),
                analysis, UUID.randomUUID()));
        assertThat(attempts.get()).isEqualTo(1);
        assertThat(failing.failures()).isEqualTo(1);
    }

    @Test
    void retryOfSamePendingRecordIsIdempotent() {
        UUID analysis = UUID.randomUUID();
        PendingStrategyMatchRecord record = pending(analysis, "tx-retry");
        TransactionTemplate tx = template();
        tx.executeWithoutResult(status ->
                recorder.persistSafely(record));
        tx.executeWithoutResult(status ->
                recorder.persistSafely(record));
        assertThat(repository.findByAnalysisExecutionId(analysis)).hasSize(1);
        assertThat(recorder.duplicates()).isGreaterThanOrEqualTo(1);
    }

    private com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluation
            evaluationFor(PendingStrategyMatchRecord pending) {
        var definition = new BuiltinStrategies().legacyOhlcTrend();
        return com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluation.match(
                definition,
                new StrategyEvaluationContextFactory().fromOhlcTrendValues(
                        pending.marketId(), "ETH/USD",
                        com.hope.trading.market_intelligence.strategy.domain.StrategyApplicability.Timeframe.M15,
                        pending.matchedAt(),
                        BigDecimal.ONE,
                        pending.matchedAt()),
                MatchedDirection.SHORT,
                pending.conditionResults(),
                BigDecimal.ONE,
                null,
                java.util.Set.of());
    }
}
