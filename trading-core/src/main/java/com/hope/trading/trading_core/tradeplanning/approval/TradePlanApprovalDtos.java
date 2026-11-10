package com.hope.trading.trading_core.tradeplanning.approval;

import com.hope.trading.trading_core.risk.infrastructure.persistence.RiskPersistence;
import com.hope.trading.trading_core.tradeplanning.infrastructure.MarketIntelligenceTradePlanningClient.PlanTransport;
import java.time.Instant;
import java.util.UUID;

public final class TradePlanApprovalDtos {
    private TradePlanApprovalDtos() { }
    public record Request(UUID tradePlanId, long tradePlanVersion, UUID riskEvaluationId) { }
    public record Response(UUID challengeId, TradePlanApprovalStatus status, UUID ownerUserId,
            UUID tradingAccountId, UUID tradePlanId, long tradePlanVersion, UUID riskEvaluationId,
            Instant expiresAt, String parameterDigest, PlanTransport plan, RiskSummary risk, String reason) { }
    public record RiskSummary(UUID evaluationId, String status, String decision, boolean approved, Instant evaluatedAt) {
        static RiskSummary of(RiskPersistence.StoredEvaluation value) {
            return new RiskSummary(value.id(), value.status(), value.decision(), value.response() != null && value.response().approved(),
                    value.response() == null ? null : value.response().evaluatedAt());
        }
    }
}
