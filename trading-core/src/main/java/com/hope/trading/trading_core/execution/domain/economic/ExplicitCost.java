package com.hope.trading.trading_core.execution.domain.economic;

import java.math.BigDecimal;
import java.util.Objects;

public record ExplicitCost(
        CostComponentStatus status,
        BigDecimal amount,
        CostEvidence evidence,
        String policyVersion
) {
    public ExplicitCost {
        Objects.requireNonNull(status, "status is required");
        policyVersion = policyVersion == null ? null : policyVersion.trim();
        if (policyVersion != null && policyVersion.isEmpty()) policyVersion = null;

        switch (status) {
            case AVAILABLE -> {
                Objects.requireNonNull(amount, "available cost amount is required");
                if (evidence == null && policyVersion == null) {
                    throw new IllegalArgumentException("available cost requires evidence or policy version");
                }
            }
            case NOT_APPLICABLE -> {
                if (amount == null || amount.compareTo(BigDecimal.ZERO) != 0) {
                    throw new IllegalArgumentException("not applicable cost must be represented by zero");
                }
            }
            case UNAVAILABLE, INSUFFICIENT_EVIDENCE, UNSUPPORTED -> {
                if (amount != null) {
                    throw new IllegalArgumentException("unavailable cost cannot contain an amount");
                }
            }
        }
    }

    public static ExplicitCost available(BigDecimal amount, CostEvidence evidence, String policyVersion) {
        return new ExplicitCost(CostComponentStatus.AVAILABLE, amount, evidence, policyVersion);
    }

    public static ExplicitCost notApplicable() {
        return new ExplicitCost(CostComponentStatus.NOT_APPLICABLE, BigDecimal.ZERO, null, null);
    }

    public static ExplicitCost unavailable() {
        return new ExplicitCost(CostComponentStatus.UNAVAILABLE, null, null, null);
    }

    public static ExplicitCost insufficientEvidence() {
        return new ExplicitCost(CostComponentStatus.INSUFFICIENT_EVIDENCE, null, null, null);
    }

    public static ExplicitCost unsupported() {
        return new ExplicitCost(CostComponentStatus.UNSUPPORTED, null, null, null);
    }

    public boolean contributesAmount() {
        return status == CostComponentStatus.AVAILABLE || status == CostComponentStatus.NOT_APPLICABLE;
    }
}
