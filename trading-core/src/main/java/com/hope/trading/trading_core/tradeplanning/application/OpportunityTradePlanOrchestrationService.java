package com.hope.trading.trading_core.tradeplanning.application;

import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.tradeplanning.domain.TradePlanningProfile;
import com.hope.trading.trading_core.tradeplanning.infrastructure.MarketIntelligenceTradePlanningClient;
import java.time.Clock;
import java.util.UUID;
import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Public trader-facing orchestration for opportunity-based plan creation
 * (STORY-0023). Trading Core owns identity/ownership/profile decisions and
 * delegates planning to Market Intelligence over its internal API.
 */
@Service
public class OpportunityTradePlanOrchestrationService {
    private final AccountRepository accounts;
    private final TradePlanningProfileService profiles;
    private final MarketIntelligenceTradePlanningClient marketIntelligence;
    private final Clock clock;

    public OpportunityTradePlanOrchestrationService(
            AccountRepository accounts, TradePlanningProfileService profiles,
            MarketIntelligenceTradePlanningClient marketIntelligence, Clock clock) {
        this.accounts = accounts;
        this.profiles = profiles;
        this.marketIntelligence = marketIntelligence;
        this.clock = clock;
    }

    public Response createFromOpportunity(
            UUID actorId, UUID opportunityId, UUID accountId, String idempotencyKey) {
        var account = accounts.findById(accountId)
                .orElseThrow(() -> failure(HttpStatus.NOT_FOUND,
                        "ACCOUNT_NOT_FOUND", "Trading account does not exist"));
        if (account.getUser() == null || !actorId.equals(account.getUser().getUserId())) {
            throw failure(HttpStatus.FORBIDDEN,
                    "ACCOUNT_FORBIDDEN", "Trading account does not belong to the authenticated user");
        }
        TradePlanningProfile profile = profiles.effective(actorId, accountId);
        if (!profile.riskBudget().currency().equalsIgnoreCase(account.getBaseCurrency())) {
            throw failure(HttpStatus.UNPROCESSABLE_ENTITY,
                    "PROFILE_CURRENCY_MISMATCH",
                    "Effective trading profile currency must match the account base currency");
        }
        var context = context(actorId, accountId, account.getBaseCurrency(), profile);
        try {
            var response = marketIntelligence.generateFromOpportunity(
                    opportunityId, idempotencyKey,
                    new MarketIntelligenceTradePlanningClient.Request(actorId, accountId, context));
            return new Response(response.tradePlanId(), response.tradePlanVersion());
        } catch (FeignException exception) {
            int status = exception.status() > 0 ? exception.status() : HttpStatus.SERVICE_UNAVAILABLE.value();
            throw failure(HttpStatus.valueOf(status),
                    "MARKET_INTELLIGENCE_REJECTED",
                    "Market Intelligence could not generate the Trade Plan");
        }
    }

    public MarketIntelligenceTradePlanningClient.PlanTransport decide(
            UUID actorId, UUID planId, long version, String decision) {
        try {
            return marketIntelligence.decide(
                    planId, version,
                    new MarketIntelligenceTradePlanningClient.DecisionRequest(actorId, decision));
        } catch (FeignException exception) {
            throw translate(exception);
        }
    }

    public MarketIntelligenceTradePlanningClient.PlanTransport load(
            UUID actorId, UUID planId, long version) {
        try {
            return marketIntelligence.load(planId, version, actorId);
        } catch (FeignException exception) {
            throw translate(exception);
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

    private AnalysisTradePlanGenerationException translate(FeignException exception) {
        int status = exception.status() > 0
                ? exception.status() : HttpStatus.SERVICE_UNAVAILABLE.value();
        return failure(HttpStatus.valueOf(status), "MARKET_INTELLIGENCE_REJECTED",
                "Market Intelligence rejected the Trade Plan operation");
    }

    private AnalysisTradePlanGenerationException failure(
            HttpStatus status, String code, String message) {
        return new AnalysisTradePlanGenerationException(code, message, status.value());
    }

    public record Response(UUID tradePlanId, long tradePlanVersion) { }
}
