package com.hope.trading.trading_core.executionauthorization;

import com.hope.trading.trading_core.tradeplanning.infrastructure.MarketIntelligenceTradePlanningClient.PlanTransport;
import java.time.Instant;
import java.util.UUID;

public final class ExecutionAuthorizationDtos {
    private ExecutionAuthorizationDtos() { }

    public record Request(UUID tradePlanId, long tradePlanVersion, UUID brokerAccountId,
                          UUID riskEvaluationId, String executionIdempotencyKey) { }

    public record StepUpRequest(String password) { }

    public record Response(UUID authorizationId, ExecutionAuthorizationStatus status,
            UUID ownerUserId, UUID agentId, UUID brokerAccountId, UUID tradingAccountId,
            UUID tradePlanId, long tradePlanVersion, UUID riskEvaluationId,
            UUID canonicalMarketId, String requestFingerprint, String confirmationUrl, Instant expiresAt,
            UUID executionIntentId, PlanTransport plan) { }
}
