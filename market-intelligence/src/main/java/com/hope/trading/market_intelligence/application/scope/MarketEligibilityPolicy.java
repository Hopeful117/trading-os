package com.hope.trading.market_intelligence.application.scope;

import com.hope.trading.market_intelligence.adapter.marketdata.MarketFactsResponse;
import com.hope.trading.market_intelligence.adapter.marketdata.MarketFactStatus;
import com.hope.trading.market_intelligence.domain.scope.MarketEligibilityReason;
import com.hope.trading.market_intelligence.domain.scope.MarketEligibilityStatus;
import com.hope.trading.market_intelligence.domain.scope.MarketFactsProvenance;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MarketEligibilityPolicy {
    public static final String POLICY_NAME = "market-eligibility-hard-gates";
    public static final String POLICY_VERSION = "1";
    private final MarketEligibilityProperties properties;

    public MarketEligibilityPolicy(MarketEligibilityProperties properties) {
        this.properties = properties;
    }

    public MarketEligibilityProperties properties() {
        return properties;
    }

    public Evaluation evaluate(MarketFactsResponse facts) {
        if (facts == null || facts.readiness() == null || facts.activity() == null) {
            return new Evaluation(MarketEligibilityStatus.NOT_EVALUABLE,
                    List.of(MarketEligibilityReason.DATA_UNAVAILABLE), null, null, null);
        }
        MarketFactsResponse.MarketReadinessResponse readiness = facts.readiness();
        MarketFactsProvenance provenance = new MarketFactsProvenance(
                facts.activity().status() == null ? null : facts.activity().status().name(),
                readiness.status() == null ? null : readiness.status().name(),
                facts.generatedAt(),
                facts.activity().observationBoundary(),
                readiness.observationBoundary(),
                readiness.calculationVersion());
        List<MarketEligibilityReason> reasons = new ArrayList<>();
        if (facts.activity().status() != MarketFactStatus.AVAILABLE) {
            addReason(reasons, reasonFor(facts.activity().status()));
        }
        MarketFactStatus status = readiness.status();
        if (status != MarketFactStatus.AVAILABLE) {
            addReason(reasons, reasonFor(status));
        }
        if (readiness.observedCompletedCandles() < properties.minimumCompletedCandles()) {
            addReason(reasons, MarketEligibilityReason.INSUFFICIENT_HISTORY);
        }
        if (readiness.syntheticCandleCount() > 0 || readiness.missingIntervalCount() > 0
                || readiness.cadenceViolationCount() > 0 || readiness.conflictingDuplicateCount() > 0) {
            addReason(reasons, MarketEligibilityReason.DATA_INTEGRITY_FAILURE);
        }
        if (!reasons.isEmpty()) {
            return new Evaluation(MarketEligibilityStatus.NOT_EVALUABLE, reasons,
                    status == null ? null : status.name(), readiness.calculationVersion(), provenance);
        }
        return new Evaluation(MarketEligibilityStatus.ELIGIBLE, List.of(),
                status == null ? null : status.name(), readiness.calculationVersion(), provenance);
    }

    private void addReason(List<MarketEligibilityReason> reasons, MarketEligibilityReason reason) {
        if (!reasons.contains(reason)) {
            reasons.add(reason);
        }
    }

    private MarketEligibilityReason reasonFor(MarketFactStatus status) {
        return switch (status == null ? MarketFactStatus.UNAVAILABLE : status) {
            case UNSUPPORTED -> MarketEligibilityReason.UNSUPPORTED_DATA;
            case STALE -> MarketEligibilityReason.STALE_DATA;
            case INSUFFICIENT_DATA -> MarketEligibilityReason.INSUFFICIENT_HISTORY;
            case UNAVAILABLE -> MarketEligibilityReason.DATA_UNAVAILABLE;
            default -> MarketEligibilityReason.DATA_UNAVAILABLE;
        };
    }

    public record Evaluation(
            MarketEligibilityStatus status,
            List<MarketEligibilityReason> reasons,
            String factsStatus,
            String factsCalculationVersion,
            MarketFactsProvenance provenance
    ) {
        public Evaluation {
            reasons = List.copyOf(reasons);
        }
    }
}
