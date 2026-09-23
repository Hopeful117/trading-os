package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.port.TradePlanningContextRepository;
import com.hope.trading.market_intelligence.application.tradeplan.ManualTradePlanningRequest;
import com.hope.trading.market_intelligence.application.tradeplan.TradePlanApplicationService;
import com.hope.trading.market_intelligence.application.tradeplan.TradePlanningResult;
import com.hope.trading.market_intelligence.domain.tradeplan.EntryStrategy;
import com.hope.trading.market_intelligence.domain.tradeplan.EntryType;
import com.hope.trading.market_intelligence.domain.tradeplan.PlanningPreferences;
import com.hope.trading.market_intelligence.domain.tradeplan.PositionSizing;
import com.hope.trading.market_intelligence.domain.tradeplan.RiskBudget;
import com.hope.trading.market_intelligence.domain.tradeplan.StopLoss;
import com.hope.trading.market_intelligence.domain.tradeplan.TakeProfit;
import com.hope.trading.market_intelligence.domain.tradeplan.TradeDirection;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlan;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanningContext;
import com.hope.trading.market_intelligence.security.MiServicePrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/intelligence/trade-plans")
public final class InternalManualTradePlanController {
    private final TradePlanApplicationService service;
    private final TradePlanningContextRepository contexts;

    public InternalManualTradePlanController(
            TradePlanApplicationService service, TradePlanningContextRepository contexts) {
        this.service = service;
        this.contexts = contexts;
    }

    @PostMapping("/manual")
    public ResponseEntity<?> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody Request request,
            Authentication authentication) {
        UUID actorId = ((MiServicePrincipal) authentication.getPrincipal())
                .requireMatchingActor(request.actorId());
        if (!actorId.equals(request.context().ownerId())
                || !request.accountId().equals(request.context().tradingAccountId())) {
            throw new IllegalArgumentException("Manual Trade Plan context does not match actor or account");
        }
        contexts.saveSnapshot(request.context().toDomain());
        TradePlanningResult result = service.createManual(request.toApplicationRequest(actorId));
        if (result instanceof TradePlanningResult.Success success) {
            TradePlan plan = success.plan();
            TradePlanningContext context = contexts.find(
                            plan.planningContext().id(), plan.planningContext().version())
                    .orElseThrow();
            return ResponseEntity.status(201).body(TradePlanResponse.from(plan, context));
        }
        TradePlanningResult.Failure failure = (TradePlanningResult.Failure) result;
        return ResponseEntity.unprocessableEntity().body(new TradePlanningFailureResponse(
                failure.reason().name(), failure.explanation(), failure.conflicts()));
    }

    public record Request(
            @NotNull UUID actorId,
            @NotNull UUID accountId,
            @NotNull Context context,
            @NotBlank String instrument,
            @NotBlank String direction,
            @NotBlank String entryType,
            BigDecimal entryPrice,
            @NotNull @Positive BigDecimal referencePrice,
            @NotNull @Positive BigDecimal stopLoss,
            @NotBlank String stopRationale,
            @NotEmpty List<@Valid Target> takeProfits,
            @NotNull @Positive BigDecimal quantity,
            @NotNull @Positive BigDecimal notional,
            @NotNull @Positive BigDecimal monetaryRisk,
            @NotNull Instant expiresAt,
            @NotBlank String expirationPolicy,
            @NotBlank String thesis,
            @NotEmpty Set<String> confirmationConditions,
            @NotEmpty Set<String> invalidationConditions,
            Set<String> managementRules
    ) {
        ManualTradePlanningRequest toApplicationRequest(UUID authenticatedActor) {
            return new ManualTradePlanningRequest(
                    context.id(), context.version(), authenticatedActor, instrument,
                    TradeDirection.valueOf(direction),
                    new EntryStrategy(EntryType.valueOf(entryType), entryPrice,
                            Set.of("Human-authored entry")),
                    new StopLoss(stopLoss, stopRationale),
                    takeProfits.stream().map(value -> new TakeProfit(
                            value.price(), value.allocationPercent())).toList(),
                    new PositionSizing(quantity, notional, monetaryRisk, context.accountCurrency()),
                    referencePrice, expiresAt, expirationPolicy, thesis,
                    confirmationConditions, invalidationConditions,
                    managementRules == null ? Set.of() : managementRules);
        }
    }

    public record Context(
            UUID id, long version, Instant capturedAt, UUID ownerId, UUID tradingAccountId,
            String accountCurrency, RiskBudgetValue riskBudget, PreferencesValue preferences
    ) {
        TradePlanningContext toDomain() {
            return new TradePlanningContext(id, version, capturedAt, ownerId, tradingAccountId,
                    accountCurrency,
                    new RiskBudget(riskBudget.amount(), riskBudget.currency(),
                            riskBudget.sourceId(), riskBudget.sourceVersion()),
                    new PlanningPreferences(preferences.id(), preferences.version(),
                            EntryType.valueOf(preferences.entryType()),
                            PlanningPreferences.StopStrategy.valueOf(preferences.stopStrategy()),
                            preferences.stopDistancePercent(),
                            PlanningPreferences.TargetStrategy.valueOf(preferences.targetStrategy()),
                            preferences.targetRiskMultiple(),
                            PlanningPreferences.PlanningHorizon.valueOf(preferences.horizon()),
                            preferences.validity()));
        }
    }

    public record RiskBudgetValue(
            BigDecimal amount, String currency, UUID sourceId, long sourceVersion) { }

    public record PreferencesValue(
            UUID id, long version, String entryType, String stopStrategy,
            BigDecimal stopDistancePercent, String targetStrategy,
            BigDecimal targetRiskMultiple, String horizon, Duration validity) { }

    public record Target(
            @NotNull @Positive BigDecimal price,
            @NotNull @Positive BigDecimal allocationPercent) { }
}
