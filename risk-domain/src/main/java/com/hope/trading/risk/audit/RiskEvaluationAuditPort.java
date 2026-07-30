package com.hope.trading.risk.audit;

/**
 * Outbound boundary for durable append-only audit storage. Infrastructure may
 * implement it; the pure domain has no persistence adapter.
 */
@FunctionalInterface
public interface RiskEvaluationAuditPort {
    void append(RiskEvaluationRecord record);
}
