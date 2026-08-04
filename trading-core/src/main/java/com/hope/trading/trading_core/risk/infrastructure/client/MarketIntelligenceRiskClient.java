package com.hope.trading.trading_core.risk.infrastructure.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.trading_core.risk.application.RiskEvaluationException;
import com.hope.trading.trading_core.risk.application.port.TradePlanRiskPort;
import feign.FeignException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "market-intelligence", contextId = "tradePlanRiskClient")
interface MarketIntelligenceRiskFeignClient {
    @GetMapping("/internal/v1/trade-plans/{id}/versions/{version}/risk-validation-snapshot")
    TradePlanTransport get(@PathVariable UUID id, @PathVariable long version);

    @PostMapping("/internal/v1/trade-plans/{id}/versions/{version}/risk-validation-acknowledgments")
    Object acknowledge(@PathVariable UUID id, @PathVariable long version, @RequestBody Acknowledgment body);
}

record Acknowledgment(UUID evaluationId, String decision, Instant evaluatedAt) { }
record TradePlanTransport(UUID tradePlanId, long tradePlanVersion, String status, Instant createdAt,
                          Context context, Execution execution, Object rationale) {
    record Context(UUID id, long version, Instant capturedAt, UUID ownerId, UUID tradingAccountId,
                   String accountCurrency, UUID riskBudgetSourceId, long riskBudgetSourceVersion,
                   UUID planningPreferencesId, long planningPreferencesVersion) { }
    record Execution(String instrument, String direction, Entry entry, StopLoss stopLoss,
                     List<Object> takeProfits, PositionSizing positionSizing,
                     BigDecimal riskRewardRatio, Object expiration, Set<String> managementRules) { }
    record Entry(String type, BigDecimal price, Set<String> conditions) { }
    record StopLoss(BigDecimal price, String rationale) { }
    record PositionSizing(BigDecimal quantity, BigDecimal notional,
                          BigDecimal expectedMonetaryRisk, String currency) { }
}

@Component
public final class MarketIntelligenceRiskClient implements TradePlanRiskPort {
    private final MarketIntelligenceRiskFeignClient client;
    private final ObjectMapper mapper;

    public MarketIntelligenceRiskClient(MarketIntelligenceRiskFeignClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public Snapshot load(UUID tradePlanId, long version) {
        TradePlanTransport value;
        try {
            value = client.get(tradePlanId, version);
        } catch (FeignException failure) {
            if (failure.status() == 404 || failure.status() == 409 || failure.status() == 422) {
                throw new RiskEvaluationException("TRADE_PLAN_COMMAND_REJECTED",
                        "Market Intelligence rejected the exact Trade Plan version", failure.status());
            }
            throw failure;
        }
        try {
            return new Snapshot(value.tradePlanId(), value.tradePlanVersion(), value.status(), value.createdAt(),
                     value.context().id(), value.context().version(), value.context().capturedAt(),
                     value.context().ownerId(), value.context().tradingAccountId(), value.context().accountCurrency(),
                     value.context().riskBudgetSourceId(), value.context().riskBudgetSourceVersion(),
                     value.context().planningPreferencesId(), value.context().planningPreferencesVersion(),
                    value.execution().instrument(), value.execution().direction(), value.execution().entry().price(),
                    value.execution().stopLoss().price(), value.execution().positionSizing().quantity(),
                    value.execution().positionSizing().notional(),
                    value.execution().positionSizing().expectedMonetaryRisk(),
                    value.execution().positionSizing().currency(), mapper.writeValueAsString(value));
        } catch (Exception failure) {
            throw new IllegalStateException("Trade Plan snapshot cannot be preserved", failure);
        }
    }

    @Override
    public void acknowledge(UUID tradePlanId, long version, UUID evaluationId,
                            String decision, Instant evaluatedAt) {
        client.acknowledge(tradePlanId, version, new Acknowledgment(evaluationId, decision, evaluatedAt));
    }
}
