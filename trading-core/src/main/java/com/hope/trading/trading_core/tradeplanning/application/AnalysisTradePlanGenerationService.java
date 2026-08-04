package com.hope.trading.trading_core.tradeplanning.application;

import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.tradeplanning.domain.TradePlanningProfile;
import com.hope.trading.trading_core.tradeplanning.infrastructure.*;
import feign.FeignException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;

@Service
public class AnalysisTradePlanGenerationService {
    private final AccountRepository accounts;
    private final TradePlanningProfileService profiles;
    private final AnalysisTradePlanContinuationRepository continuations;
    private final MarketIntelligenceTradePlanningClient marketIntelligence;
    private final Clock clock;

    public AnalysisTradePlanGenerationService(
            AccountRepository accounts, TradePlanningProfileService profiles,
            AnalysisTradePlanContinuationRepository continuations,
            MarketIntelligenceTradePlanningClient marketIntelligence, Clock clock) {
        this.accounts = accounts; this.profiles = profiles;
        this.continuations = continuations; this.marketIntelligence = marketIntelligence;
        this.clock = clock;
    }

    @Transactional
    public synchronized Response generate(
            UUID actorId, UUID analysisExecutionId, UUID accountId, String key) {
        if (key == null || key.isBlank() || key.length() > 200) {
            throw failure("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key is required", 400);
        }
        var account = accounts.findById(accountId)
                .orElseThrow(() -> failure("ACCOUNT_NOT_FOUND", "Account not found", 404));
        if (account.getUser() == null || !actorId.equals(account.getUser().getUserId())) {
            throw failure("ACCOUNT_FORBIDDEN", "Account does not belong to the actor", 403);
        }
        TradePlanningProfile profile = profiles.effective(actorId, accountId);
        if (!profile.riskBudget().currency().equalsIgnoreCase(account.getBaseCurrency())) {
            throw failure("PLANNING_CONTEXT_INCOMPLETE",
                    "Profile currency does not match account currency", 422);
        }
        var replay = continuations
                .findByAnalysisExecutionIdAndActorIdAndAccountIdAndIdempotencyKey(
                        analysisExecutionId, actorId, accountId, key);
        if (replay.isPresent()) {
            AnalysisTradePlanContinuationEntity value = replay.get();
            if (!value.profileId().equals(profile.id())
                    || value.profileVersion() != profile.version()) {
                throw failure("IDEMPOTENCY_CONFLICT",
                        "Idempotency key was used with another effective profile", 409);
            }
            if ("COMPLETED".equals(value.state())) {
                return new Response(value.tradePlanId(), value.tradePlanVersion());
            }
            return call(value, actorId, analysisExecutionId, accountId, account.getBaseCurrency(), profile, key);
        }
        UUID contextId = UUID.nameUUIDFromBytes((
                actorId + ":" + accountId + ":" + analysisExecutionId + ":" + key
                        + ":" + profile.id() + ":" + profile.version())
                .getBytes(StandardCharsets.UTF_8));
        AnalysisTradePlanContinuationEntity continuation = continuations.saveAndFlush(
                AnalysisTradePlanContinuationEntity.pending(
                        analysisExecutionId, actorId, accountId, key, contextId,
                        1, clock.instant(), profile.id(), profile.version(),
                        clock.instant()));
        return call(continuation, actorId, analysisExecutionId, accountId,
                account.getBaseCurrency(), profile, key);
    }

    private Response call(
            AnalysisTradePlanContinuationEntity continuation, UUID actorId,
            UUID analysisId, UUID accountId, String accountCurrency,
            TradePlanningProfile profile, String key) {
        var budget = profile.riskBudget(); var preferences = profile.preferences();
        var context = new MarketIntelligenceTradePlanningClient.Context(
                continuation.contextId(), continuation.contextVersion(),
                continuation.contextCapturedAt(), actorId, accountId, accountCurrency,
                new MarketIntelligenceTradePlanningClient.RiskBudget(
                        budget.amount(), budget.currency(), budget.sourceId(), budget.sourceVersion()),
                new MarketIntelligenceTradePlanningClient.Preferences(
                        preferences.id(), preferences.version(), preferences.entryType().name(),
                        preferences.stopStrategy().name(), preferences.stopDistancePercent(),
                        preferences.targetStrategy().name(), preferences.targetRiskMultiple(),
                        preferences.horizon().name(), preferences.validity()));
        try {
            var response = marketIntelligence.generate(
                    analysisId, key,
                    new MarketIntelligenceTradePlanningClient.Request(actorId, accountId, context));
            continuation.complete(response.tradePlanId(), response.tradePlanVersion(), clock.instant());
            continuations.save(continuation);
            return new Response(response.tradePlanId(), response.tradePlanVersion());
        } catch (FeignException exception) {
            int status = exception.status() > 0 ? exception.status() : 503;
            throw failure("MARKET_INTELLIGENCE_REJECTED",
                    "Market Intelligence could not generate the Trade Plan", status);
        }
    }

    private AnalysisTradePlanGenerationException failure(
            String code, String message, int status) {
        return new AnalysisTradePlanGenerationException(code, message, status);
    }

    public record Response(UUID tradePlanId, long tradePlanVersion) { }
}
