package com.hope.trading.market_intelligence.strategy.application;

import com.hope.trading.market_intelligence.domain.observation.Observation;
import com.hope.trading.market_intelligence.domain.observation.ObservationEvidence;
import com.hope.trading.market_intelligence.strategy.domain.StrategyApplicability;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shadow-mode parity monitor (Story 0010): compares the legacy OHLC trend
 * decision with the bootstrap StrategyEvaluator decision without affecting any
 * trader-facing behavior.
 *
 * <p>Diagnostics are bounded: detailed parity results are DEBUG-level, only
 * mismatches are WARN-level. Aggregate counters are kept in memory.</p>
 */
@Component
public class ShadowStrategyParityMonitor {

    private static final Logger log = LoggerFactory.getLogger(ShadowStrategyParityMonitor.class);

    private final StrategyEvaluationContextFactory contextFactory;
    private final StrategyEvaluationService evaluationService;
    private final BuiltinStrategies builtins;
    private final StrategyMatchRecorder matchRecorder;

    private final AtomicLong comparisons = new AtomicLong();
    private final AtomicLong matchesAgreed = new AtomicLong();
    private final AtomicLong mismatches = new AtomicLong();

    public ShadowStrategyParityMonitor(
            StrategyEvaluationContextFactory contextFactory,
            StrategyEvaluationService evaluationService,
            BuiltinStrategies builtins,
            StrategyMatchRecorder matchRecorder
    ) {
        this.contextFactory = contextFactory;
        this.evaluationService = evaluationService;
        this.builtins = builtins;
        this.matchRecorder = matchRecorder;
    }

    /**
     * Runs a shadow evaluation against the legacy observation outcome. Never
     * throws into the production pipeline; never mutates trader-facing state.
     *
     * <p>Story 0011: a MATCH evaluation additionally registers an after-commit
     * StrategyMatch recording intent (shadow persistence, see
     * {@link StrategyMatchRecorder}).</p>
     */
    public void compareWithLegacyDecision(Observation observation, UUID marketId,
            UUID analysisExecutionId, Instant evaluatedAt) {
        try {
            String legacyDirection = legacyDirection(observation);
            BigDecimal priceChange = observation.evidence().stream()
                    .map(evidence -> evidence.measurements().get("priceChange"))
                    .filter(Objects::nonNull)
                    .findFirst().orElse(null);
            Instant observedAt = observation.evidence().stream()
                    .map(ObservationEvidence::observedAt)
                    .filter(Objects::nonNull)
                    .findFirst().orElse(evaluatedAt);

            StrategyEvaluation evaluation =
                    evaluationService.evaluate(builtins.legacyOhlcTrend(),
                            contextFactory.fromOhlcTrendValues(marketId,
                                    observation.instrument(),
                                    StrategyApplicability.Timeframe.M15,
                                    evaluatedAt, priceChange, observedAt));

            comparisons.incrementAndGet();
            boolean agree = agreement(evaluation, legacyDirection);
            if (agree) {
                matchesAgreed.incrementAndGet();
                log.debug("Shadow strategy parity OK analysis={} legacy={} status={}",
                        observation.id(), legacyDirection, evaluation.status());
            } else {
                mismatches.incrementAndGet();
                log.warn("Shadow strategy parity MISMATCH analysis={} market={} legacy={} "
                                + "evaluatorStatus={} evaluatorDirection={} digest={}",
                        observation.id(), marketId, legacyDirection,
                        evaluation.status(), evaluation.direction().orElse(null),
                        evaluation.contextDigest());
            }
            matchRecorder.recordAfterCommit(evaluation, analysisExecutionId, observation.id());
        } catch (RuntimeException exception) {
            mismatches.incrementAndGet();
            log.warn("Shadow strategy parity diagnostic failed market={} message={}",
                    marketId, exception.toString());
        }
    }

    public long comparisons() { return comparisons.get(); }

    public long agreements() { return matchesAgreed.get(); }

    public long mismatches() { return mismatches.get(); }

    private boolean agreement(StrategyEvaluation evaluation, String legacyDirection) {
        String evaluatorDirection = evaluation.isMatch()
                ? evaluation.direction().orElseThrow().name()
                : null;
        return Objects.equals(legacyDirection, evaluatorDirection);
    }

    /** Legacy decision derived from the observation type suffix (LONG/SHORT). */
    static String legacyDirection(Observation observation) {
        String value = observation.type().value();
        if (value.endsWith("LONG")) return "LONG";
        if (value.endsWith("SHORT")) return "SHORT";
        return null;
    }
}
