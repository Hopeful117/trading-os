package com.hope.trading.trading_core.tradeplanning.infrastructure;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@FeignClient(name = "market-intelligence", contextId = "analysisTradePlanningClient")
public interface MarketIntelligenceTradePlanningClient {
    @PostMapping("/internal/v1/intelligence/analyses/{analysisExecutionId}/trade-plans")
    Response generate(
            @PathVariable UUID analysisExecutionId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody Request request);

    @PostMapping("/internal/v1/intelligence/opportunities/{opportunityId}/trade-plans")
    Response generateFromOpportunity(
            @PathVariable UUID opportunityId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody Request request);

    @PostMapping("/internal/v1/trade-plans/{planId}/versions/{version}/decisions")
    PlanTransport decide(
            @PathVariable UUID planId,
            @PathVariable long version,
            @RequestBody DecisionRequest request);

    @GetMapping("/internal/v1/trade-plans/{planId}/versions/{version}")
    PlanTransport load(
            @PathVariable UUID planId,
            @PathVariable long version,
            @RequestParam UUID actorId);

    record Request(UUID actorId, UUID accountId, Context context) { }
    record Context(UUID id, long version, Instant capturedAt, UUID ownerId,
                   UUID tradingAccountId, String accountCurrency,
                   RiskBudget riskBudget, Preferences preferences) { }
    record RiskBudget(BigDecimal amount, String currency, UUID sourceId,
                      long sourceVersion) { }
    record Preferences(UUID id, long version, String entryType, String stopStrategy,
                       BigDecimal stopDistancePercent, String targetStrategy,
                       BigDecimal targetRiskMultiple, String horizon, Duration validity) { }
    record Response(UUID tradePlanId, long tradePlanVersion) { }
    record DecisionRequest(UUID actorId, String decision) { }
    record PlanTransport(UUID id, long version, Long previousVersion, String status,
                         UUID planningContextId, long planningContextVersion,
                         Instant contextCapturedAt, String instrument, String direction,
                         String entryType, BigDecimal entryPrice, BigDecimal stopLoss,
                         java.util.List<BigDecimal> takeProfits, BigDecimal quantity,
                         BigDecimal notional, BigDecimal monetaryRisk, BigDecimal riskReward,
                         Instant expiresAt, String thesis, java.util.Set<UUID> opportunityIds,
                         java.util.Set<UUID> observationIds, java.util.Set<UUID> aiAnalysisIds,
                         java.util.Set<String> confirmationConditions,
                         java.util.Set<String> invalidationConditions,
                         java.util.Set<String> managementRules, Instant createdAt, UUID tradingAccountId) { }
}
