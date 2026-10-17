package com.hope.trading.trading_core.execution.domain.economic;

import java.math.BigDecimal;
import java.util.Objects;

public record TransactionCosts(
        BigDecimal executionImpact,
        ExplicitCost commissionComponent,
        ExplicitCost fundingComponent,
        ExplicitCost otherExplicitCosts,
        BigDecimal total,
        String transactionCostModelVersion
) {
    public TransactionCosts(
            BigDecimal executionImpact,
            BigDecimal fee,
            BigDecimal funding,
            BigDecimal total,
            CostEvidence feeEvidence,
            CostEvidence fundingEvidence
    ) {
        this(executionImpact,
                ExplicitCost.available(fee, feeEvidence, "legacy-supplied-cost-v1"),
                ExplicitCost.available(funding, fundingEvidence, "legacy-supplied-cost-v1"),
                ExplicitCost.notApplicable(), total, "legacy-supplied-cost-v1");
    }

    public TransactionCosts {
        requireNonNegative(executionImpact, "executionImpact");
        Objects.requireNonNull(commissionComponent, "commissionComponent is required");
        Objects.requireNonNull(fundingComponent, "fundingComponent is required");
        Objects.requireNonNull(otherExplicitCosts, "otherExplicitCosts is required");
        requireNonNegativeIfAvailable(commissionComponent, "commission");
        requireNonNegativeIfAvailable(otherExplicitCosts, "other explicit costs");
        transactionCostModelVersion = required(transactionCostModelVersion, "transactionCostModelVersion");
        BigDecimal explicitTotal = explicitTotal(commissionComponent, fundingComponent, otherExplicitCosts);
        if (total != null && explicitTotal != null) {
            BigDecimal expected = executionImpact.add(explicitTotal);
            if (total.compareTo(expected) != 0) {
                throw new IllegalArgumentException("total must equal executionImpact + explicit costs");
            }
        }
    }

    public BigDecimal fee() {
        return commissionComponent.amount();
    }

    public BigDecimal funding() {
        return fundingComponent.amount();
    }

    public CostEvidence feeEvidence() {
        return commissionComponent.evidence();
    }

    public CostEvidence fundingEvidence() {
        return fundingComponent.evidence();
    }

    public CostComponentStatus feeStatus() {
        return commissionComponent.status();
    }

    public CostComponentStatus fundingStatus() {
        return fundingComponent.status();
    }

    public BigDecimal explicitTotal() {
        return explicitTotal(commissionComponent, fundingComponent, otherExplicitCosts);
    }

    public CostCompleteness completeness() {
        boolean commissionKnown = commissionComponent.contributesAmount();
        boolean fundingKnown = fundingComponent.contributesAmount();
        boolean otherKnown = otherExplicitCosts.contributesAmount();
        if (commissionKnown && fundingKnown && otherKnown) return CostCompleteness.FULLY_COSTED;
        if (commissionKnown || fundingKnown || otherKnown) return CostCompleteness.PARTIALLY_COSTED;
        return CostCompleteness.INSUFFICIENT_EVIDENCE;
    }

    private static BigDecimal explicitTotal(ExplicitCost commission, ExplicitCost funding, ExplicitCost other) {
        if (!commission.contributesAmount() || !funding.contributesAmount() || !other.contributesAmount()) return null;
        return commission.amount().add(funding.amount()).add(other.amount());
    }

    private static void requireNonNegative(BigDecimal value, String field) {
        if (Objects.requireNonNull(value, field).signum() < 0) {
            throw new IllegalArgumentException(field + " cannot be negative");
        }
    }

    private static void requireNonNegativeIfAvailable(ExplicitCost cost, String field) {
        if (cost.status() == CostComponentStatus.AVAILABLE && cost.amount().signum() < 0) {
            throw new IllegalArgumentException(field + " cannot be negative");
        }
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }
}
