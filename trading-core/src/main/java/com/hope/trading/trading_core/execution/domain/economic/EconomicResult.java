package com.hope.trading.trading_core.execution.domain.economic;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

public record EconomicResult(
        EconomicResultStatus status,
        CostCompleteness completeness,
        BigDecimal referenceNotional,
        BigDecimal executionNotional,
        SimulatedExecution execution,
        TransactionCosts costs,
        StrategyIntent intent,
        ReferenceMarketState entryMarketState,
        ReferenceMarketState exitMarketState,
        SimulatedExecution entryExecution,
        SimulatedExecution exitExecution,
        SimulatedExecutionStatus executionStatus,
        BigDecimal referenceMarketReturn,
        BigDecimal grossStrategyPnl,
        BigDecimal executionAdjustedPnl,
        BigDecimal netPnl,
        BigDecimal netReturn,
        String executionModelVersion,
        String transactionCostModelVersion,
        String calculationFingerprint,
        String reason,
        BigDecimal closedQuantity,
        BigDecimal residualExposure
) {
    public EconomicResult(
            EconomicResultStatus status,
            CostCompleteness completeness,
            BigDecimal referenceNotional,
            BigDecimal executionNotional,
            SimulatedExecution execution,
            TransactionCosts costs,
            StrategyIntent intent,
            ReferenceMarketState entryMarketState,
            ReferenceMarketState exitMarketState,
            SimulatedExecution entryExecution,
            SimulatedExecution exitExecution,
            SimulatedExecutionStatus executionStatus,
            BigDecimal referenceMarketReturn,
            BigDecimal grossStrategyPnl,
            BigDecimal executionAdjustedPnl,
            BigDecimal netPnl,
            BigDecimal netReturn,
            String executionModelVersion,
            String transactionCostModelVersion,
            String calculationFingerprint,
            String reason
    ) {
        this(status, completeness, referenceNotional, executionNotional, execution, costs, intent,
                entryMarketState, exitMarketState, entryExecution, exitExecution, executionStatus,
                referenceMarketReturn, grossStrategyPnl, executionAdjustedPnl, netPnl, netReturn,
                executionModelVersion, transactionCostModelVersion, calculationFingerprint, reason,
                deriveClosedQuantity(intent, entryExecution, exitExecution, entryMarketState),
                deriveResidualExposure(intent, entryExecution, exitExecution, entryMarketState));
    }

    public EconomicResult {
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(completeness, "completeness is required");
        if (closedQuantity != null && closedQuantity.signum() < 0) {
            throw new IllegalArgumentException("closedQuantity cannot be negative");
        }
        if (residualExposure != null && residualExposure.signum() < 0) {
            throw new IllegalArgumentException("residualExposure cannot be negative");
        }
        if (status == EconomicResultStatus.AVAILABLE) {
            Objects.requireNonNull(referenceNotional, "referenceNotional is required for available results");
            Objects.requireNonNull(executionNotional, "executionNotional is required for available results");
            Objects.requireNonNull(execution, "execution is required for available results");
            Objects.requireNonNull(costs, "costs are required for available results");
            Objects.requireNonNull(executionStatus, "executionStatus is required for available results");
            Objects.requireNonNull(executionModelVersion, "executionModelVersion is required for available results");
            Objects.requireNonNull(transactionCostModelVersion,
                    "transactionCostModelVersion is required for available results");
            calculationFingerprint = required(calculationFingerprint, "calculationFingerprint");
            boolean lifecycle = intent != null || entryMarketState != null || exitMarketState != null
                    || exitExecution != null;
            if (lifecycle) {
                Objects.requireNonNull(intent, "intent is required for available lifecycle results");
                Objects.requireNonNull(entryMarketState, "entryMarketState is required for available lifecycle results");
                Objects.requireNonNull(exitMarketState, "exitMarketState is required for available lifecycle results");
                Objects.requireNonNull(entryExecution, "entryExecution is required for available lifecycle results");
                Objects.requireNonNull(exitExecution, "exitExecution is required for available lifecycle results");
                Objects.requireNonNull(referenceMarketReturn,
                        "referenceMarketReturn is required for available lifecycle results");
                Objects.requireNonNull(grossStrategyPnl,
                        "grossStrategyPnl is required for available lifecycle results");
                Objects.requireNonNull(executionAdjustedPnl,
                        "executionAdjustedPnl is required for available lifecycle results");
            } else {
                if (!execution.equals(entryExecution) || exitExecution != null) {
                    throw new IllegalArgumentException("available single-leg results must identify their entry execution");
                }
            }
        }
        if (status != EconomicResultStatus.AVAILABLE && netPnl != null) {
            throw new IllegalArgumentException("non-available results cannot contain authoritative net PnL");
        }
        if (netPnl != null && (status != EconomicResultStatus.AVAILABLE
                || completeness != CostCompleteness.FULLY_COSTED
                || entryExecution == null || exitExecution == null
                || !entryExecution.isFullyFilled() || !exitExecution.isFullyFilled())) {
            throw new IllegalArgumentException("authoritative net PnL requires a fully costed closed lifecycle");
        }
        if (netReturn != null && netPnl == null) {
            throw new IllegalArgumentException("net return requires net PnL");
        }
        if (reason != null && reason.isBlank()) reason = null;
    }

    public static EconomicResult singleLeg(
            BigDecimal referenceNotional,
            BigDecimal executionNotional,
            SimulatedExecution execution,
            TransactionCosts costs,
            String calculationFingerprint
    ) {
        Objects.requireNonNull(execution, "execution is required");
        Objects.requireNonNull(costs, "costs are required");
        return new EconomicResult(
                EconomicResultStatus.AVAILABLE,
                costs.completeness(),
                referenceNotional,
                executionNotional,
                execution,
                costs,
                null,
                null,
                null,
                execution,
                null,
                execution.status(),
                null,
                null,
                null,
                null,
                null,
                execution.executionModelVersion(),
                costs.transactionCostModelVersion(),
                calculationFingerprint,
                null
        );
    }

    public static EconomicResult unavailable(EconomicResultStatus status, String reason) {
        return unavailable(status, CostCompleteness.INSUFFICIENT_EVIDENCE, reason);
    }

    public static EconomicResult unavailable(
            EconomicResultStatus status,
            CostCompleteness completeness,
            String reason
    ) {
        if (status == EconomicResultStatus.AVAILABLE) {
            throw new IllegalArgumentException("AVAILABLE requires calculated values");
        }
        return new EconomicResult(status, completeness, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, digest(status.name() + "|" + completeness.name() + "|" + Objects.toString(reason, "")), reason);
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }

    private static String digest(String material) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private static BigDecimal deriveClosedQuantity(StrategyIntent intent,
                                                   SimulatedExecution entry,
                                                   SimulatedExecution exit,
                                                   ReferenceMarketState entryState) {
        if (exit != null) return exit.filledQuantity();
        if (intent != null && entry != null && entryState != null) return BigDecimal.ZERO;
        return entry == null ? null : entry.filledQuantity();
    }

    private static BigDecimal deriveResidualExposure(StrategyIntent intent,
                                                     SimulatedExecution entry,
                                                     SimulatedExecution exit,
                                                     ReferenceMarketState entryState) {
        if (intent == null || entry == null || entryState == null) return null;
        return entry.filledQuantity().subtract(exit == null ? BigDecimal.ZERO : exit.filledQuantity());
    }
}
