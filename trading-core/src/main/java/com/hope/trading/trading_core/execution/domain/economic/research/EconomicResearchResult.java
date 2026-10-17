package com.hope.trading.trading_core.execution.domain.economic.research;

import com.hope.trading.trading_core.execution.domain.economic.EconomicResult;

import java.util.Objects;

public record EconomicResearchResult(
        String candidateFingerprint,
        String objectiveOutcomeFingerprint,
        EconomicResult economicResult,
        EconomicResearchResultDisposition disposition,
        String reason,
        String resultFingerprint
) {
    public EconomicResearchResult {
        candidateFingerprint = required(candidateFingerprint, "candidateFingerprint");
        Objects.requireNonNull(disposition, "disposition is required");
        if (disposition == EconomicResearchResultDisposition.EVALUATED && economicResult == null) {
            throw new IllegalArgumentException("evaluated results require an economic result");
        }
        reason = reason == null || reason.isBlank() ? null : reason.trim();
        String calculationFingerprint = economicResult == null ? null : economicResult.calculationFingerprint();
        String derivedFingerprint = EconomicResearchFingerprint.of(candidateFingerprint, objectiveOutcomeFingerprint,
                calculationFingerprint, disposition.name(), reason);
        if (resultFingerprint != null && !resultFingerprint.isBlank()
                && !resultFingerprint.trim().equals(derivedFingerprint)) {
            throw new IllegalArgumentException("resultFingerprint does not match result semantics");
        }
        resultFingerprint = derivedFingerprint;
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }
}
