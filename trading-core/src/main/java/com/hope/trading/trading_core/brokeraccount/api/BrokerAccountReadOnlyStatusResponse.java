package com.hope.trading.trading_core.brokeraccount.api;

import com.hope.trading.trading_core.broker.dto.BrokerAccountSnapshotV2Dto;

import java.util.List;

public record BrokerAccountReadOnlyStatusResponse(
        String status,
        boolean executionAllowed,
        boolean riskEvaluationAllowed,
        BrokerAccountSnapshotV2Dto snapshot,
        Readiness readiness) {

    public BrokerAccountReadOnlyStatusResponse(String status,
                                               boolean executionAllowed,
                                               boolean riskEvaluationAllowed,
                                               BrokerAccountSnapshotV2Dto snapshot) {
        this(status, executionAllowed, riskEvaluationAllowed, snapshot,
                Readiness.from(snapshot));
    }

    public record Readiness(String accountObservation,
                            String t0Risk,
                            String liveExecution,
                            List<String> unavailableFacts,
                            List<String> derivedFacts) {
        public Readiness {
            unavailableFacts = List.copyOf(unavailableFacts == null ? List.of() : unavailableFacts);
            derivedFacts = List.copyOf(derivedFacts == null ? List.of() : derivedFacts);
        }

        public static Readiness from(BrokerAccountSnapshotV2Dto snapshot) {
            if (snapshot == null) {
                return new Readiness("UNAVAILABLE", "BLOCKED", "BLOCKED", List.of(), List.of());
            }

            String observation = "COMPLETE".equals(snapshot.completeness())
                    && "FRESH".equals(snapshot.freshness()) ? "COMPLETE"
                    : "FRESH".equals(snapshot.freshness()) ? "PARTIAL" : "STALE";
            boolean baseFactsAvailable = "FRESH".equals(snapshot.freshness())
                    && snapshot.account() != null
                    && snapshot.account().balance() != null
                    && isAvailable(snapshot.positionAvailability())
                    && isAvailable(snapshot.orderAvailability());
            // The status endpoint has no selected policy or invariant context.
            // Do not present structural facts as a positive T0 evaluation.
            String t0Risk = baseFactsAvailable ? "NOT_EVALUATED" : "BLOCKED";
            List<String> derived = snapshot.account() != null
                    && snapshot.account().equityFact() != null
                    && "BROKER_DERIVED".equals(snapshot.account().equityFact().provenance())
                    ? List.of("EQUITY") : List.of();
            return new Readiness(observation, t0Risk, "BLOCKED",
                    snapshot.unavailabilityReasons(), derived);
        }

        private static boolean isAvailable(
                com.hope.trading.trading_core.broker.dto.SnapshotCollectionAvailability availability) {
            return availability == com.hope.trading.trading_core.broker.dto.SnapshotCollectionAvailability.AVAILABLE_EMPTY
                    || availability == com.hope.trading.trading_core.broker.dto.SnapshotCollectionAvailability.AVAILABLE_NON_EMPTY;
        }
    }
}
