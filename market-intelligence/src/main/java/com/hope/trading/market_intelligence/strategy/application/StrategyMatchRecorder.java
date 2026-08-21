package com.hope.trading.market_intelligence.strategy.application;

import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shadow StrategyMatch recording for Story 0011.
 *
 * <p>Called inside the production pipeline transaction (T1), it snapshots an
 * immutable {@link PendingStrategyMatchRecord} and only registers an
 * after-commit intent. Persistence happens AFTER T1 commits, in a separate
 * REQUIRES_NEW transaction (T2), so a StrategyMatch can never reference
 * evidence that was rolled back.</p>
 *
 * <p>Story 0011 is shadow migration infrastructure: persistence failure after
 * commit is recorded as a bounded diagnostic (WARN + in-memory counters) and
 * never propagates into AnalysisExecution, PipelineRun, Observation,
 * TradingOpportunity or ActiveScan. Known temporary crash window: if the
 * process dies between T1 commit and after-commit recording, legacy truth
 * exists but the match is missing; logical creation stays idempotent on retry.
 * Story 0012 MUST revisit this transaction/recovery model before
 * TradingOpportunity depends on StrategyMatch.</p>
 */
@Component
public class StrategyMatchRecorder {

    private static final Logger log = LoggerFactory.getLogger(StrategyMatchRecorder.class);

    private final StrategyMatchPersister persister;

    private final AtomicLong intents = new AtomicLong();
    private final AtomicLong persisted = new AtomicLong();
    private final AtomicLong duplicates = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();

    public StrategyMatchRecorder(StrategyMatchPersister persister) {
        this.persister = persister;
    }

    /**
     * Snapshots a MATCH evaluation into an immutable pending record and
     * registers an after-commit recording intent. Must be called while the
     * pipeline transaction is active. Non-MATCH evaluations are ignored:
     * only MATCH creates a StrategyMatch.
     */
    public void recordAfterCommit(
            StrategyEvaluation evaluation,
            UUID analysisExecutionId,
            UUID observationId
    ) {
        if (!evaluation.isMatch()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            failures.incrementAndGet();
            log.warn("StrategyMatch recording skipped: no active transaction "
                    + "analysis={} market={}", analysisExecutionId, evaluation.marketId());
            return;
        }
        PendingStrategyMatchRecord pending =
                PendingStrategyMatchRecord.fromEvaluation(
                        evaluation, analysisExecutionId, observationId);
        intents.incrementAndGet();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                persistSafely(pending);
            }
        });
    }

    /** Visible for idempotent-retry tooling and tests; never throws. */
    void persistSafely(PendingStrategyMatchRecord pending) {
        try {
            persister.persist(pending).ifPresent(result -> {
                if (result.created()) {
                    persisted.incrementAndGet();
                    log.info("StrategyMatch persisted matchId={} strategy={}v{} market={} "
                                    + "analysis={} direction={} digest={}",
                            result.match().matchId(), result.match().strategyId(),
                            result.match().strategyVersion(), result.match().marketId(),
                            pending.analysisExecutionId(), result.match().direction(),
                            result.match().contextDigest());
                } else {
                    duplicates.incrementAndGet();
                    log.debug("StrategyMatch already existed matchId={} digest={}",
                            result.match().matchId(), result.match().contextDigest());
                }
            });
        } catch (RuntimeException exception) {
            failures.incrementAndGet();
            log.warn("StrategyMatch shadow persistence failed after commit "
                            + "analysis={} market={} digest={} message={}",
                    pending.analysisExecutionId(), pending.marketId(),
                    pending.contextDigest(), exception.toString());
        }
    }

    public long intents() { return intents.get(); }

    public long persisted() { return persisted.get(); }

    public long duplicates() { return duplicates.get(); }

    public long failures() { return failures.get(); }
}
