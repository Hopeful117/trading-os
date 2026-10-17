package com.hope.trading.trading_core.execution.application.command;

import com.hope.trading.trading_core.execution.domain.economic.StrategyIntent;
import com.hope.trading.trading_core.execution.domain.economic.TransactionCosts;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record HistoricalEconomicEvaluationRequest(
        UUID marketId,
        StrategyIntent entryIntent,
        Instant entryExecutionCutoff,
        Instant exitExecutionCutoff,
        Instant researchCutoff,
        TransactionCosts transactionCosts,
        String executionModelVersion,
        String transactionCostModelVersion
) {
    public HistoricalEconomicEvaluationRequest {
        Objects.requireNonNull(marketId, "marketId is required");
        Objects.requireNonNull(entryIntent, "entryIntent is required");
        Objects.requireNonNull(entryExecutionCutoff, "entryExecutionCutoff is required");
        Objects.requireNonNull(exitExecutionCutoff, "exitExecutionCutoff is required");
        Objects.requireNonNull(researchCutoff, "researchCutoff is required");
        Objects.requireNonNull(transactionCosts, "transactionCosts is required");
        executionModelVersion = required(executionModelVersion, "executionModelVersion");
        transactionCostModelVersion = required(transactionCostModelVersion, "transactionCostModelVersion");
        if (!entryIntent.decisionAt().equals(entryExecutionCutoff)) {
            throw new IllegalArgumentException("zero-latency entry requires cutoff to equal decisionAt");
        }
        if (exitExecutionCutoff.isBefore(entryExecutionCutoff)) {
            throw new IllegalArgumentException("exit cutoff cannot precede entry cutoff");
        }
        if (researchCutoff.isBefore(exitExecutionCutoff)) {
            throw new IllegalArgumentException("research cutoff cannot precede exit cutoff");
        }
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }
}
