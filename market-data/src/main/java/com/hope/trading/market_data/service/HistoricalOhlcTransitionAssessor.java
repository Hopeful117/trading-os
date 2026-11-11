package com.hope.trading.market_data.service;

import com.hope.trading.market_data.model.HistoricalClosureEvidence;
import com.hope.trading.market_data.model.HistoricalContinuityPolicy;
import com.hope.trading.market_data.model.HistoricalOhlcEvidence;
import com.hope.trading.market_data.model.HistoricalOhlcTransition;
import com.hope.trading.market_data.model.HistoricalTransitionClassification;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Base transition classification; consumer acceptance belongs to the caller. */
public final class HistoricalOhlcTransitionAssessor {
    private HistoricalOhlcTransitionAssessor() {
    }

    public static HistoricalOhlcTransition assess(
            HistoricalOhlcEvidence previous,
            HistoricalOhlcEvidence next,
            HistoricalContinuityPolicy policy,
            List<HistoricalClosureEvidence> closureEvidence
    ) {
        Objects.requireNonNull(previous, "previous is required");
        Objects.requireNonNull(next, "next is required");
        Objects.requireNonNull(policy, "policy is required");
        Duration cadence = policy.interval().getDuration();
        Duration elapsed = Duration.between(previous.closeTime(), next.openTime());
        if (!valid(previous, policy) || !valid(next, policy)
                || !previous.marketId().equals(next.marketId())) {
            return transition(previous, next, cadence, elapsed,
                    HistoricalTransitionClassification.INVALID_OBSERVATION, null,
                    "one or both observations are invalid for the continuity policy");
        }
        if (!previous.closeTime().isBefore(next.openTime())) {
            return transition(previous, next, cadence, elapsed,
                    HistoricalTransitionClassification.INVALID_OBSERVATION, null,
                    "observations are not chronologically ordered");
        }
        if (previous.closeTime().equals(next.openTime())) {
            return transition(previous, next, cadence, elapsed,
                    HistoricalTransitionClassification.CONTIGUOUS, null, "observations are adjacent");
        }

        List<HistoricalClosureEvidence> applicable = (closureEvidence == null ? List.<HistoricalClosureEvidence>of() : closureEvidence)
                .stream()
                .filter(value -> applies(value, previous, next, policy))
                .toList();
        if (applicable.size() == 1) {
            return transition(previous, next, cadence, elapsed,
                    HistoricalTransitionClassification.VERIFIED_CLOSURE, applicable.getFirst(),
                    "scoped closure evidence covers the complete transition");
        }
        return transition(previous, next, cadence, elapsed,
                applicable.isEmpty() ? HistoricalTransitionClassification.UNKNOWN_CLOSURE_EVIDENCE
                        : HistoricalTransitionClassification.UNKNOWN_CLOSURE_EVIDENCE,
                null, applicable.isEmpty() ? "no applicable closure evidence" : "closure evidence is ambiguous");
    }

    private static boolean applies(HistoricalClosureEvidence evidence,
                                   HistoricalOhlcEvidence previous,
                                   HistoricalOhlcEvidence next,
                                   HistoricalContinuityPolicy policy) {
        return evidence.marketId().equals(previous.marketId())
                && (policy.marketId() == null || policy.marketId().equals(previous.marketId()))
                && (policy.canonicalSymbol() == null || evidence.canonicalSymbol().equals(policy.canonicalSymbol()))
                && (policy.venue() == null || evidence.venue().equals(policy.venue()))
                && evidence.interval() == policy.interval()
                && evidence.interval() == next.interval()
                && evidence.closureStartUtc().compareTo(previous.closeTime()) <= 0
                && evidence.closureEndUtc().compareTo(next.openTime()) >= 0
                && !evidence.knownAt().isAfter(policy.evidenceCutoff())
                && !evidence.effectiveFrom().isAfter(previous.closeTime())
                && !evidence.effectiveTo().isBefore(next.openTime());
    }

    private static boolean valid(HistoricalOhlcEvidence evidence, HistoricalContinuityPolicy policy) {
        return evidence.interval() == policy.interval()
                && evidence.closed()
                && !evidence.synthetic()
                && evidence.openTime().plus(policy.interval().getDuration()).equals(evidence.closeTime())
                && evidence.high().compareTo(evidence.low()) >= 0
                && evidence.open().compareTo(evidence.low()) >= 0
                && evidence.open().compareTo(evidence.high()) <= 0
                && evidence.close().compareTo(evidence.low()) >= 0
                && evidence.close().compareTo(evidence.high()) <= 0;
    }

    private static HistoricalOhlcTransition transition(
            HistoricalOhlcEvidence previous,
            HistoricalOhlcEvidence next,
            Duration cadence,
            Duration elapsed,
            HistoricalTransitionClassification classification,
            HistoricalClosureEvidence evidence,
            String reason
    ) {
        return new HistoricalOhlcTransition(previous.closeTime(), next.openTime(), cadence, elapsed,
                classification, evidence, reason);
    }
}
