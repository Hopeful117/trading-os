package com.hope.trading.trading_core.risk.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RiskEvaluationModels {
    private RiskEvaluationModels() {
    }

    public record Command(UUID actorId, UUID tradePlanId, long tradePlanVersion,
                          UUID accountId, String idempotencyKey, Instant requestedAt) {
        public Command {
            if (actorId == null || tradePlanId == null || accountId == null || requestedAt == null) {
                throw new IllegalArgumentException("command identity is required");
            }
            if (tradePlanVersion < 1) throw new IllegalArgumentException("version starts at 1");
            idempotencyKey = required(idempotencyKey, "Idempotency-Key");
            if (idempotencyKey.length() > 160) throw new IllegalArgumentException("Idempotency-Key is too long");
        }
    }

    public record Response(UUID evaluationId, UUID tradePlanId, long tradePlanVersion,
                           UUID accountId, String status, String decision, boolean approved,
                           List<Reason> reasons, List<Reason> warnings, Map<String, BigDecimal> metrics,
                           Instant evaluatedAt, Trace trace) {
        public Response {
            reasons = List.copyOf(reasons);
            warnings = List.copyOf(warnings);
            metrics = Map.copyOf(metrics);
        }
    }

    public record Reason(String code, String ruleVersion, String severity, String message,
                         Map<String, BigDecimal> metrics) {
        public Reason { metrics = Map.copyOf(metrics); }
    }

    public record Trace(UUID correlationId, String engineVersion,
                        Map<String, String> policyVersions, Map<String, String> ruleVersions,
                        Map<String, Long> snapshotVersions) {
        public Trace {
            policyVersions = Map.copyOf(policyVersions);
            ruleVersions = Map.copyOf(ruleVersions);
            snapshotVersions = Map.copyOf(snapshotVersions);
        }
    }

    static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.strip();
    }
}
