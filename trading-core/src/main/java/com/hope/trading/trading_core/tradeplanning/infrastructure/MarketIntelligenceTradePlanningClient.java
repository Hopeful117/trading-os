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
}
