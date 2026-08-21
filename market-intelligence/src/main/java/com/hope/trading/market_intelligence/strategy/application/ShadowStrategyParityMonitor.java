package com.hope.trading.market_intelligence.strategy.application;

import com.hope.trading.market_intelligence.domain.observation.Observation;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Parity diagnostics between the legacy OHLC trend observation type and the
 * bootstrap StrategyEvaluator decision (Story 0010 instrumentation).
 *
 * <p>Since Story 0012 this component performs NO persistence and no
 * after-commit delivery: StrategyMatch persistence is inline required truth
 * owned by {@link StrategyMatchPersister}. Diagnostics remain bounded
 * (DEBUG details, WARN mismatches, in-memory counters).</p>
 */
@Component
public class ShadowStrategyParityMonitor {

    private static final Logger log = LoggerFactory.getLogger(ShadowStrategyParityMonitor.class);

    private final LiveStrategyEvaluationRunner evaluationRunner;

    private final AtomicLong comparisons = new AtomicLong();
    private final AtomicLong matchesAgreed = new AtomicLong();
    private final AtomicLong mismatches = new AtomicLong();

    public ShadowStrategyParityMonitor(LiveStrategyEvaluationRunner evaluationRunner) {
        this.evaluationRunner = evaluationRunner;
    }

    /**
     * Records parity diagnostics for one legacy outcome. Never throws into the
     * production pipeline; never mutates trader-facing state.
     */
    public void compareWithLegacyDecision(
            StrategyEvaluation evaluation, Observation observation, UUID marketId) {
        try {
            comparisons.incrementAndGet();
            String legacyDirection = legacyDirection(observation);
            String evaluatorDirection = evaluation.isMatch()
                    ? evaluation.direction().orElseThrow().name()
                    : null;
            boolean agree = Objects.equals(legacyDirection, evaluatorDirection);
            if (agree) {
                matchesAgreed.incrementAndGet();
                log.debug("Strategy parity OK analysis={} legacy={} status={}",
                        observation.id(), legacyDirection, evaluation.status());
            } else {
                mismatches.incrementAndGet();
                log.warn("Strategy parity MISMATCH analysis={} market={} legacy={} "
                                + "evaluatorStatus={} evaluatorDirection={} digest={}",
                        observation.id(), marketId, legacyDirection,
                        evaluation.status(), evaluatorDirection,
                        evaluation.contextDigest());
            }
        } catch (RuntimeException exception) {
            mismatches.incrementAndGet();
            log.warn("Strategy parity diagnostic failed market={} message={}",
                    marketId, exception.toString());
        }
    }

    public long comparisons() { return comparisons.get(); }

    public long agreements() { return matchesAgreed.get(); }

    public long mismatches() { return mismatches.get(); }

    /** Legacy decision derived from the observation type suffix (LONG/SHORT). */
    static String legacyDirection(Observation observation) {
        String value = observation.type().value();
        if (value.endsWith("LONG")) return "LONG";
        if (value.endsWith("SHORT")) return "SHORT";
        return null;
    }
}
