package com.hope.trading.trading_core.tradeplanning.application;

import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.tradeplanning.api.ManualTradePlanRequest;
import com.hope.trading.trading_core.tradeplanning.domain.TradePlanningProfile;
import com.hope.trading.trading_core.tradeplanning.infrastructure.MarketIntelligenceTradePlanningClient;
import com.hope.trading.trading_core.market_data.service.MarketService;
import feign.FeignException;
import java.time.Clock;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public final class ManualTradePlanOrchestrationService {
    private final AccountRepository accounts;
    private final TradePlanningProfileService profiles;
    private final MarketService markets;
    private final MarketIntelligenceTradePlanningClient marketIntelligence;
    private final Clock clock;

    public ManualTradePlanOrchestrationService(
            AccountRepository accounts, TradePlanningProfileService profiles,
            MarketService markets, MarketIntelligenceTradePlanningClient marketIntelligence, Clock clock) {
        this.accounts = accounts;
        this.profiles = profiles;
        this.markets = markets;
        this.marketIntelligence = marketIntelligence;
        this.clock = clock;
    }

    public Response create(UUID actorId, ManualTradePlanRequest request, String idempotencyKey) {
        var account = accounts.findById(request.accountId())
                .orElseThrow(() -> failure(HttpStatus.NOT_FOUND,
                        "ACCOUNT_NOT_FOUND", "Trading account does not exist"));
        if (account.getUser() == null || !actorId.equals(account.getUser().getUserId())) {
            throw failure(HttpStatus.FORBIDDEN,
                    "ACCOUNT_FORBIDDEN", "Trading account does not belong to the authenticated user");
        }
        var market = markets.findAll().stream()
                .filter(candidate -> request.marketId().equals(candidate.getMarketId()))
                .findFirst()
                .orElseThrow(() -> failure(HttpStatus.NOT_FOUND,
                        "MARKET_NOT_FOUND", "Market does not exist"));
        if (market.getMarketState() == null || !market.getMarketState().isTradable()) {
            throw failure(HttpStatus.UNPROCESSABLE_ENTITY,
                    "MARKET_NOT_TRADABLE", "Market is not currently tradable");
        }
        TradePlanningProfile profile = profiles.effective(actorId, request.accountId());
        if (!profile.riskBudget().currency().equalsIgnoreCase(account.getBaseCurrency())) {
            throw failure(HttpStatus.UNPROCESSABLE_ENTITY,
                    "PROFILE_CURRENCY_MISMATCH",
                    "Effective trading profile currency must match the account base currency");
        }
        String entryType = request.entryType().trim().toUpperCase();
        if (!entryType.equals("MARKET") && !entryType.equals("LIMIT")) {
            throw failure(HttpStatus.UNPROCESSABLE_ENTITY,
                    "UNSUPPORTED_ENTRY_TYPE", "Only MARKET and LIMIT entries are supported");
        }
        String direction = request.direction().trim().toUpperCase();
        if (!direction.equals("LONG") && !direction.equals("SHORT")) {
            throw failure(HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_DIRECTION", "Only LONG and SHORT directions are supported");
        }
        if (entryType.equals("LIMIT")
                && (request.entryPrice() == null || request.entryPrice().signum() <= 0)) {
            throw failure(HttpStatus.UNPROCESSABLE_ENTITY,
                    "ENTRY_PRICE_REQUIRED", "LIMIT entries require a positive entry price");
        }
        if (entryType.equals("MARKET") && request.entryPrice() != null) {
            throw failure(HttpStatus.UNPROCESSABLE_ENTITY,
                    "MARKET_ENTRY_PRICE_FORBIDDEN", "MARKET entries must not provide a limit price");
        }
        var context = context(actorId, request.accountId(), account.getBaseCurrency(), profile);
        BigDecimal notional = request.quantity().multiply(
                entryType.equals("LIMIT") ? request.entryPrice() : request.referencePrice());
        if (notional.signum() <= 0) {
            throw failure(HttpStatus.UNPROCESSABLE_ENTITY,
                    "NOTIONAL_INVALID", "Manual Trade Plan notional must be positive");
        }
        try {
            var response = marketIntelligence.createManual(
                    idempotencyKey,
                    new MarketIntelligenceTradePlanningClient.ManualRequest(
                            actorId, request.accountId(), context, market.getSymbol(), direction, entryType,
                            request.entryPrice(), request.referencePrice(), request.stopLoss(),
                            request.stopRationale(), request.takeProfits().stream()
                                    .map(value -> new MarketIntelligenceTradePlanningClient.TakeProfit(
                                            value.price(), value.allocationPercent())).toList(),
                            request.quantity(), notional, request.monetaryRisk(),
                            clock.instant().plus(profile.preferences().validity()), "PROFILE_VALIDITY", request.thesis(),
                            request.confirmationConditions(), request.invalidationConditions(),
                            request.managementRules() == null ? java.util.Set.of() : request.managementRules()));
            return new Response(response.id(), response.version());
        } catch (FeignException exception) {
            int status = exception.status() > 0 ? exception.status() : HttpStatus.SERVICE_UNAVAILABLE.value();
            throw failure(HttpStatus.valueOf(status), "MARKET_INTELLIGENCE_REJECTED",
                    "Market Intelligence could not create the manual Trade Plan");
        }
    }

    private MarketIntelligenceTradePlanningClient.Context context(
            UUID actorId, UUID accountId, String accountCurrency, TradePlanningProfile profile) {
        var budget = profile.riskBudget();
        var preferences = profile.preferences();
        return new MarketIntelligenceTradePlanningClient.Context(
                UUID.randomUUID(), 1, clock.instant(), actorId, accountId, accountCurrency,
                new MarketIntelligenceTradePlanningClient.RiskBudget(
                        budget.amount(), budget.currency(), budget.sourceId(), budget.sourceVersion()),
                new MarketIntelligenceTradePlanningClient.Preferences(
                        preferences.id(), preferences.version(), preferences.entryType().name(),
                        preferences.stopStrategy().name(), preferences.stopDistancePercent(),
                        preferences.targetStrategy().name(), preferences.targetRiskMultiple(),
                        preferences.horizon().name(), preferences.validity()));
    }

    private ManualTradePlanException failure(HttpStatus status, String code, String message) {
        return new ManualTradePlanException(code, message, status.value());
    }

    public record Response(UUID tradePlanId, long tradePlanVersion) { }
}
