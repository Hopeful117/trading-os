package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.port.TradePlanningContextRepository;
import com.hope.trading.market_intelligence.application.tradeplan.*;
import com.hope.trading.market_intelligence.domain.tradeplan.*;
import com.hope.trading.market_intelligence.security.MiUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/intelligence/trade-plans")
public final class ManualTradePlanController {
    private final TradePlanApplicationService service;
    private final TradePlanningContextRepository contexts;

    public ManualTradePlanController(
            TradePlanApplicationService service, TradePlanningContextRepository contexts) {
        this.service = service;
        this.contexts = contexts;
    }

    @PostMapping("/manual")
    public ResponseEntity<?> create(
            @Valid @RequestBody Request request, Authentication authentication) {
        UUID actorId = authenticatedActor(authentication);
        TradePlanningResult result = service.createManual(request.toApplicationRequest(actorId));
        if (result instanceof TradePlanningResult.Success success) {
            TradePlan plan = success.plan();
            TradePlanningContext context = contexts.find(
                            plan.planningContext().id(), plan.planningContext().version())
                    .orElseThrow();
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(TradePlanResponse.from(plan, context));
        }
        TradePlanningResult.Failure failure = (TradePlanningResult.Failure) result;
        return ResponseEntity.unprocessableEntity().body(new TradePlanningFailureResponse(
                failure.reason().name(), failure.explanation(), failure.conflicts()));
    }

    private static UUID authenticatedActor(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof MiUserPrincipal principal)) {
            throw new IllegalStateException("Authenticated user is required");
        }
        return principal.userId();
    }

    public record Request(
            @NotNull UUID planningContextId,
            @Positive long contextVersion,
            @NotBlank String instrument,
            @NotNull TradeDirection direction,
            @NotNull EntryType entryType,
            @Positive BigDecimal entryPrice,
            @NotNull @Positive BigDecimal referencePrice,
            @NotNull @Positive BigDecimal stopLoss,
            @NotBlank String stopRationale,
            @NotEmpty List<@Valid Target> takeProfits,
            @NotNull @Positive BigDecimal quantity,
            @NotNull @Positive BigDecimal notional,
            @NotNull @Positive BigDecimal monetaryRisk,
            @NotBlank String currency,
            @NotNull Instant expiresAt,
            @NotBlank String expirationPolicy,
            @NotBlank String thesis,
            @NotEmpty Set<String> confirmationConditions,
            @NotEmpty Set<String> invalidationConditions,
            Set<String> managementRules
    ) {
        ManualTradePlanningRequest toApplicationRequest(UUID actorId) {
            return new ManualTradePlanningRequest(
                    planningContextId, contextVersion, actorId, instrument, direction,
                    new EntryStrategy(entryType, entryPrice, Set.of("Human-authored entry")),
                    new StopLoss(stopLoss, stopRationale),
                    takeProfits.stream().map(Target::toDomain).toList(),
                    new PositionSizing(quantity, notional, monetaryRisk, currency),
                    referencePrice, expiresAt, expirationPolicy, thesis,
                    confirmationConditions, invalidationConditions,
                    managementRules == null ? Set.of() : managementRules);
        }
    }

    public record Target(
            @NotNull @Positive BigDecimal price,
            @NotNull @Positive BigDecimal allocationPercent
    ) {
        TakeProfit toDomain() {
            return new TakeProfit(price, allocationPercent);
        }
    }
}
