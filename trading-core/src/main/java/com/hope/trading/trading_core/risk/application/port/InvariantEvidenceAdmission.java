package com.hope.trading.trading_core.risk.application.port;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Resolves which configured invariants lack independently admissible facts. */
public final class InvariantEvidenceAdmission {
    private InvariantEvidenceAdmission() { }

    public static String structuralFailure(RiskFactsProvider.Snapshot snapshot, UUID expectedSourceId) {
        if (snapshot == null) return "BROKER_RISK_FACTS_UNAVAILABLE";
        if (expectedSourceId != null && !expectedSourceId.equals(snapshot.sourceId())) {
            return "BROKER_RISK_FACTS_IDENTITY_MISMATCH";
        }
        if (snapshot.account() == null || snapshot.sourceVersion() < 1 || snapshot.observedAt() == null
                || snapshot.assetBalances() == null || snapshot.positions() == null
                || snapshot.closedTrades() == null || snapshot.ledgerEntries() == null) {
            return "BROKER_RISK_FACTS_STRUCTURE_INVALID";
        }
        if (snapshot.account().valuationAsset() == null || snapshot.account().valuationAsset().isBlank()
                || snapshot.assetBalances().entrySet().stream().anyMatch(entry -> entry.getKey() == null
                || entry.getKey().isBlank() || entry.getValue() == null)) {
            return "BROKER_RISK_FACTS_CURRENCY_OR_BALANCE_INVALID";
        }
        if (snapshot.complete()) return null;
        if (snapshot.unavailabilityReasons() == null || snapshot.unavailabilityReasons().isEmpty()) {
            return "BROKER_RISK_FACTS_INCOMPLETE";
        }
        boolean hasScopedReason = snapshot.unavailabilityReasons().stream()
                .anyMatch(InvariantEvidenceAdmission::isInvariantScoped);
        return snapshot.unavailabilityReasons().stream()
                .filter(reason -> !isInvariantScoped(reason)
                        && (!"OBSERVATION_INCOMPLETE".equals(reason) || !hasScopedReason))
                .findFirst()
                .orElse(null);
    }

    public static Map<String, String> resolve(RiskFactsProvider.Snapshot snapshot,
                                               RiskFactRequirements requirements,
                                               com.hope.trading.trading_core.model.Account account,
                                               boolean paper) {
        Map<String, String> incomplete = new LinkedHashMap<>();
        if (requirements == null) return incomplete;
        for (var entry : requirements.invariants().entrySet()) {
            String reason = missingFact(snapshot, entry.getValue(), requirements, account, paper);
            if (reason != null && entry.getValue().mandatory()) incomplete.put(entry.getKey(), reason);
        }
        return Map.copyOf(incomplete);
    }

    private static String missingFact(RiskFactsProvider.Snapshot snapshot,
                                      RiskFactRequirements.InvariantRequirements invariant,
                                      RiskFactRequirements requirements,
                                      com.hope.trading.trading_core.model.Account account,
                                      boolean paper) {
        for (var fact : invariant.facts()) {
            String reason = switch (fact) {
                case BALANCE -> snapshot == null || snapshot.account() == null
                        || snapshot.account().balance() == null ? "BROKER_BALANCE_MISSING" : null;
                case EQUITY -> snapshot == null || !RiskFactsProvider.equityAdmissible(
                        snapshot.account(), requirements, paper)
                        ? equityReason(snapshot) : null;
                case POSITIONS -> snapshot == null || snapshot.positions() == null
                        || unavailable(snapshot, "POSITION") ? "BROKER_POSITIONS_UNAVAILABLE" : null;
                case PROTECTIVE_STOPS -> protectiveStopsReason(snapshot);
                case STARTING_BALANCE -> snapshot == null || snapshot.account() == null
                        || (snapshot.account().startingBalance() == null
                        && (account == null || account.getStartingBalance() == null))
                        ? "BROKER_STARTING_BALANCE_MISSING" : null;
                case CLOSED_DEALS -> snapshot == null
                        || snapshot.dealAvailability() == RiskFactsProvider.DealAvailability.UNAVAILABLE_OR_ABSENT
                        || snapshot.dealAvailability() == RiskFactsProvider.DealAvailability.TRUNCATED
                        ? "BROKER_DEAL_HISTORY_UNAVAILABLE" : null;
            };
            if (reason != null) return reason;
        }
        return null;
    }

    private static String equityReason(RiskFactsProvider.Snapshot snapshot) {
        if (snapshot != null && snapshot.account() != null && snapshot.account().equityFact() != null
                && !snapshot.account().equityFact().reasons().isEmpty()) {
            return snapshot.account().equityFact().reasons().getFirst();
        }
        return "BROKER_EQUITY_PROVENANCE_OR_QUALITY_INVALID";
    }

    private static String protectiveStopsReason(RiskFactsProvider.Snapshot snapshot) {
        if (snapshot == null || snapshot.positions() == null || unavailable(snapshot, "POSITION")) {
            return "BROKER_PROTECTIVE_STOPS_UNAVAILABLE";
        }
        boolean invalid = snapshot.positions().stream().anyMatch(position -> position == null
                || position.protectionStatus() != com.hope.trading.risk.domain.RiskTypes.ProtectionStatus.PROTECTED
                || position.protectiveStops() == null || position.protectiveStops().isEmpty());
        return invalid ? "BROKER_PROTECTIVE_STOPS_UNAVAILABLE" : null;
    }

    private static boolean unavailable(RiskFactsProvider.Snapshot snapshot, String prefix) {
        return snapshot.unavailabilityReasons() != null
                && snapshot.unavailabilityReasons().stream().anyMatch(reason -> reason != null
                && reason.toUpperCase(java.util.Locale.ROOT).contains(prefix));
    }

    private static boolean isInvariantScoped(String reason) {
        if (reason == null || reason.isBlank()) return false;
        String normalized = reason.toUpperCase(java.util.Locale.ROOT);
        return normalized.startsWith("EQUITY") || normalized.startsWith("BROKER_EQUITY")
                || normalized.startsWith("BALANCE") || normalized.startsWith("BROKER_BALANCE")
                || normalized.startsWith("POSITION") || normalized.startsWith("BROKER_POSITION")
                || normalized.startsWith("PROTECTIVE_STOP") || normalized.startsWith("BROKER_PROTECTIVE")
                || normalized.startsWith("STARTING_BALANCE") || normalized.startsWith("BROKER_STARTING")
                || normalized.startsWith("DEAL") || normalized.startsWith("BROKER_DEAL")
                || normalized.startsWith("CLOSED_TRADE") || normalized.startsWith("USED_MARGIN")
                || normalized.startsWith("FREE_MARGIN") || normalized.startsWith("UNREALIZED_PNL")
                || normalized.startsWith("PAPER_ACCOUNT_VALUATION")
                || normalized.startsWith("PAPER_TRADE_FACTS") || normalized.startsWith("ORDER_");
    }
}
