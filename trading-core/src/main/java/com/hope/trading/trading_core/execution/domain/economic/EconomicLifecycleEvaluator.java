package com.hope.trading.trading_core.execution.domain.economic;

import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Objects;

public final class EconomicLifecycleEvaluator {
    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;

    public EconomicResult evaluate(
            StrategyIntent intent,
            ReferenceMarketState entryState,
            ReferenceMarketState exitState,
            SimulatedExecution entry,
            SimulatedExecution exit,
            TransactionCosts costs,
            String executionModelVersion,
            String transactionCostModelVersion
    ) {
        return evaluate(intent, entryState, exitState, entry, exit, costs, executionModelVersion,
                transactionCostModelVersion, exit == null ? null : exit.executedAt());
    }

    public EconomicResult evaluate(
            StrategyIntent intent,
            ReferenceMarketState entryState,
            ReferenceMarketState exitState,
            SimulatedExecution entry,
            SimulatedExecution exit,
            TransactionCosts costs,
            String executionModelVersion,
            String transactionCostModelVersion,
            Instant researchCutoff
    ) {
        if (intent == null || entryState == null || exitState == null || entry == null || exit == null
                || costs == null || researchCutoff == null) {
            return EconomicResult.unavailable(EconomicResultStatus.INSUFFICIENT_DATA,
                    "intent, market states, executions, costs and research cutoff are required");
        }
        if (!intent.instrument().equals(entryState.instrument())
                || !intent.instrument().equals(exitState.instrument())
                || entryState.productType() != exitState.productType()
                || !entryState.marketId().equals(exitState.marketId())) {
            return unavailableLifecycle(EconomicResultStatus.INVALID_INPUT,
                    "lifecycle inputs must refer to the same instrument, market and product", intent,
                    entryState, exitState, entry, exit, costs, executionModelVersion,
                    transactionCostModelVersion, researchCutoff);
        }
        if (entry.side() != intent.side()
                || exit.side() != opposite(intent.side())
                || exit.executedAt().isBefore(entry.executedAt())) {
            return unavailableLifecycle(EconomicResultStatus.INVALID_INPUT,
                    "entry and exit sides or timestamps are inconsistent", intent,
                    entryState, exitState, entry, exit, costs, executionModelVersion,
                    transactionCostModelVersion, researchCutoff);
        }
        if (entryState.knownAt().isAfter(entry.executedAt())
                || exitState.knownAt().isAfter(exit.executedAt())
                || entryState.knownAt().isAfter(intent.decisionAt())
                || intent.decisionAt().isAfter(entry.executedAt())) {
            return unavailableLifecycle(EconomicResultStatus.STALE,
                    "a lifecycle input was known after its applicable execution time", intent,
                    entryState, exitState, entry, exit, costs, executionModelVersion,
                    transactionCostModelVersion, researchCutoff);
        }
        if (!eligible(costs.commissionComponent(), entry.executedAt(), exit.executedAt(), researchCutoff)
                || !eligible(costs.fundingComponent(), entry.executedAt(), exit.executedAt(), researchCutoff)
                || !eligible(costs.otherExplicitCosts(), entry.executedAt(), exit.executedAt(), researchCutoff)) {
            return unavailableLifecycle(EconomicResultStatus.STALE,
                    "cost evidence was not eligible at the lifecycle execution times", intent,
                    entryState, exitState, entry, exit, costs, executionModelVersion,
                    transactionCostModelVersion, researchCutoff);
        }
        if (executionModelVersion == null || executionModelVersion.isBlank()
                || transactionCostModelVersion == null || transactionCostModelVersion.isBlank()) {
            return unavailableLifecycle(EconomicResultStatus.INVALID_INPUT,
                    "execution and transaction-cost policy versions are required", intent,
                    entryState, exitState, entry, exit, costs, executionModelVersion,
                    transactionCostModelVersion, researchCutoff);
        }
        if (!executionModelVersion.equals(entry.executionModelVersion())
                || !executionModelVersion.equals(exit.executionModelVersion())
                || !transactionCostModelVersion.equals(costs.transactionCostModelVersion())) {
            return unavailableLifecycle(EconomicResultStatus.INVALID_INPUT,
                    "lifecycle policy versions do not match the supplied inputs", intent,
                    entryState, exitState, entry, exit, costs, executionModelVersion,
                    transactionCostModelVersion, researchCutoff);
        }
        if (exit.filledQuantity().compareTo(entry.filledQuantity()) > 0) {
            return unavailableLifecycle(EconomicResultStatus.INVALID_INPUT,
                    "exit cannot exceed entry exposure", intent, entryState, exitState, entry, exit,
                    costs, executionModelVersion, transactionCostModelVersion, researchCutoff);
        }
        if (entry.filledQuantity().signum() == 0 || exit.filledQuantity().signum() == 0) {
            SimulatedExecutionStatus executionStatus = aggregateStatus(entry, exit);
            String fingerprint = fingerprint(intent, entryState, exitState, entry, exit, costs,
                    executionModelVersion, transactionCostModelVersion, researchCutoff,
                    costs.completeness(), executionStatus);
            return new EconomicResult(EconomicResultStatus.EXECUTION_UNAVAILABLE, costs.completeness(), null, null,
                    entry, costs, intent, entryState, exitState, entry, exit, executionStatus,
                    null, null, null, null, null, executionModelVersion, transactionCostModelVersion,
                    fingerprint, "lifecycle contains no filled quantity", exit.filledQuantity(),
                    entry.filledQuantity().subtract(exit.filledQuantity()));
        }

        BigDecimal closedQuantity = exit.filledQuantity();
        BigDecimal referenceNotional = entryState.referencePrice().multiply(closedQuantity, CALCULATION_CONTEXT);
        BigDecimal referenceMarketReturn = directionalReturn(entryState.referencePrice(),
                exitState.referencePrice(), intent.side());
        BigDecimal grossStrategyPnl = directionalDifference(exitState.referencePrice(),
                entryState.referencePrice(), intent.side()).multiply(closedQuantity, CALCULATION_CONTEXT);
        BigDecimal executionAdjustedPnl = directionalDifference(exit.averageExecutionPrice(),
                entry.averageExecutionPrice(), intent.side()).multiply(closedQuantity, CALCULATION_CONTEXT);

        SimulatedExecutionStatus executionStatus = aggregateStatus(entry, exit);
        boolean closed = entry.isFullyFilled() && exit.isFullyFilled()
                && entry.filledQuantity().compareTo(exit.filledQuantity()) == 0;
        CostCompleteness completeness = costs.completeness();
        EconomicResultStatus resultStatus = closed && completeness == CostCompleteness.FULLY_COSTED
                ? EconomicResultStatus.AVAILABLE : EconomicResultStatus.DEGRADED;
        BigDecimal netPnl = null;
        if (resultStatus == EconomicResultStatus.AVAILABLE) {
            netPnl = executionAdjustedPnl.subtract(costs.explicitTotal(), CALCULATION_CONTEXT);
        }

        String fingerprint = fingerprint(intent, entryState, exitState, entry, exit, costs,
                executionModelVersion, transactionCostModelVersion, researchCutoff, completeness, executionStatus);
        return new EconomicResult(resultStatus, completeness, referenceNotional,
                entry.averageExecutionPrice().multiply(closedQuantity, CALCULATION_CONTEXT),
                entry, costs, intent, entryState, exitState, entry, exit, executionStatus,
                referenceMarketReturn, grossStrategyPnl, executionAdjustedPnl, netPnl, null,
                executionModelVersion, transactionCostModelVersion, fingerprint,
                closed ? null : "lifecycle is not fully closed", closedQuantity,
                entry.filledQuantity().subtract(exit.filledQuantity()));
    }

    private EconomicResult unavailableLifecycle(
            EconomicResultStatus status,
            String reason,
            StrategyIntent intent,
            ReferenceMarketState entryState,
            ReferenceMarketState exitState,
            SimulatedExecution entry,
            SimulatedExecution exit,
            TransactionCosts costs,
            String executionModelVersion,
            String transactionCostModelVersion,
            Instant researchCutoff
    ) {
        SimulatedExecutionStatus executionStatus = aggregateStatus(entry, exit);
        String fingerprint = fingerprint(intent, entryState, exitState, entry, exit, costs,
                executionModelVersion, transactionCostModelVersion, researchCutoff,
                costs.completeness(), executionStatus);
        return new EconomicResult(status, costs.completeness(), null, null, entry, costs, intent,
                entryState, exitState, entry, exit, executionStatus, null, null, null, null, null,
                executionModelVersion, transactionCostModelVersion, fingerprint, reason, null, null);
    }

    private StrategyIntent.Side opposite(StrategyIntent.Side side) {
        return side == StrategyIntent.Side.BUY ? StrategyIntent.Side.SELL : StrategyIntent.Side.BUY;
    }

    private BigDecimal directionalDifference(BigDecimal exit, BigDecimal entry, StrategyIntent.Side side) {
        return side == StrategyIntent.Side.BUY ? exit.subtract(entry) : entry.subtract(exit);
    }

    private BigDecimal directionalReturn(BigDecimal entry, BigDecimal exit, StrategyIntent.Side side) {
        return directionalDifference(exit, entry, side).divide(entry, CALCULATION_CONTEXT);
    }

    private SimulatedExecutionStatus aggregateStatus(SimulatedExecution entry, SimulatedExecution exit) {
        if (entry.status() == SimulatedExecutionStatus.FILLED
                && exit.status() == SimulatedExecutionStatus.FILLED
                && entry.filledQuantity().compareTo(exit.filledQuantity()) == 0) {
            return SimulatedExecutionStatus.FILLED;
        }
        if (entry.filledQuantity().signum() == 0 || exit.filledQuantity().signum() == 0) {
            return SimulatedExecutionStatus.UNFILLED;
        }
        return SimulatedExecutionStatus.PARTIALLY_FILLED;
    }

    private String fingerprint(
            StrategyIntent intent,
            ReferenceMarketState entryState,
            ReferenceMarketState exitState,
            SimulatedExecution entry,
            SimulatedExecution exit,
            TransactionCosts costs,
            String executionModelVersion,
            String transactionCostModelVersion,
            Instant researchCutoff,
            CostCompleteness completeness,
            SimulatedExecutionStatus executionStatus
    ) {
        return digest(canonicalMaterial(
                intent.instrument(), intent.side().name(), intent.orderType().name(),
                intent.quantity().toPlainString(), intent.decisionAt().toString(),
                entryState.marketId().toString(), entryState.evidenceFingerprint(),
                entryState.referencePrice().toPlainString(), exitState.evidenceFingerprint(),
                exitState.referencePrice().toPlainString(),
                executionMaterial(entry), executionMaterial(exit),
                costs.executionImpact().toPlainString(), costMaterial(costs.commissionComponent()),
                costMaterial(costs.fundingComponent()), costMaterial(costs.otherExplicitCosts()),
                executionModelVersion, transactionCostModelVersion, researchCutoff.toString(),
                completeness.name(), executionStatus.name()));
    }

    private String canonicalMaterial(String... values) {
        StringBuilder material = new StringBuilder();
        for (String value : values) {
            String normalized = Objects.toString(value, "");
            material.append(normalized.length()).append(':').append(normalized);
        }
        return material.toString();
    }

    private String digest(String material) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private String executionMaterial(SimulatedExecution execution) {
        return canonicalMaterial(execution.side().name(), execution.requestedQuantity().toPlainString(),
                execution.filledQuantity().toPlainString(),
                Objects.toString(execution.averageExecutionPrice(), ""), execution.executedAt().toString(),
                execution.status().name(), execution.executionModelVersion(), execution.sourceFingerprint(),
                execution.executionFingerprint(), Objects.toString(execution.reason(), ""));
    }

    private String costMaterial(ExplicitCost cost) {
        return canonicalMaterial(cost.status().name(), Objects.toString(cost.amount(), ""),
                Objects.toString(cost.evidence() == null ? null : cost.evidence().fingerprint(), ""),
                Objects.toString(cost.policyVersion(), ""));
    }

    private boolean eligible(ExplicitCost cost, Instant entryTime, Instant exitTime, Instant researchCutoff) {
        if (cost.status() != CostComponentStatus.AVAILABLE || cost.evidence() == null) return true;
        return !cost.evidence().knownAt().isAfter(researchCutoff)
                && cost.evidence().eligibleAt(entryTime) && cost.evidence().eligibleAt(exitTime);
    }
}
