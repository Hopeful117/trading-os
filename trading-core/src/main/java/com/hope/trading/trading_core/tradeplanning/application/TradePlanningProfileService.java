package com.hope.trading.trading_core.tradeplanning.application;

import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.tradeplanning.domain.TradePlanningProfile;
import com.hope.trading.trading_core.tradeplanning.domain.TradePlanningProfile.PlanningPreferences;
import com.hope.trading.trading_core.tradeplanning.domain.TradePlanningProfile.RiskBudget;
import java.time.Clock;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TradePlanningProfileService {
    private final AccountRepository accounts;
    private final TradePlanningProfileRepository profiles;
    private final Clock clock;



    @Transactional
    public TradePlanningProfile create(UUID actorId, Values values) {
        return append(actorId, UUID.randomUUID(), 1, values);
    }

    @Transactional
    public TradePlanningProfile createVersion(UUID actorId, UUID profileId, Values values) {
        TradePlanningProfile latest = profiles.findLatest(profileId)
                .orElseThrow(() -> failure("PLANNING_PROFILE_NOT_FOUND", "Trade Planning Profile not found", 404));
        requireOwner(actorId, latest);
        return append(actorId, profileId, latest.version() + 1, values);
    }

    @Transactional
    public TradePlanningProfile assign(UUID actorId, UUID accountId, UUID profileId, long version) {
        requireAccountOwner(actorId, accountId);
        TradePlanningProfile profile = profiles.find(profileId, version)
                .orElseThrow(() -> failure("PLANNING_PROFILE_NOT_FOUND", "Trade Planning Profile version not found", 404));
        requireOwner(actorId, profile);
        profiles.assign(accountId, profileId, version, actorId, clock.instant());
        return profile;
    }

    @Transactional(readOnly = true)
    public TradePlanningProfile effective(UUID actorId, UUID accountId) {
        requireAccountOwner(actorId, accountId);
        TradePlanningProfile profile = profiles.findAssigned(accountId)
                .orElseThrow(() -> failure("PLANNING_PROFILE_MISSING", "Account has no effective Trade Planning Profile", 422));
        requireOwner(actorId, profile);
        return profile;
    }

    private TradePlanningProfile append(UUID actorId, UUID id, long version, Values values) {
        RiskBudget budget = new RiskBudget(values.riskBudgetAmount(), values.currency(), id, version);
        PlanningPreferences preferences = new PlanningPreferences(id, version, values.entryType(),
                values.stopStrategy(), values.stopDistancePercent(), values.targetStrategy(),
                values.targetRiskMultiple(), values.horizon(), values.validity());
        return profiles.append(new TradePlanningProfile(id, version, actorId, budget, preferences, clock.instant()));
    }

    private void requireAccountOwner(UUID actorId, UUID accountId) {
        var account = accounts.findById(accountId)
                .orElseThrow(() -> failure("ACCOUNT_NOT_FOUND", "Account not found", 404));
        if (account.getUser() == null || !actorId.equals(account.getUser().getUserId())) {
            throw failure("ACCOUNT_FORBIDDEN", "Account does not belong to the actor", 403);
        }
    }
    private static void requireOwner(UUID actorId, TradePlanningProfile profile) {
        if (!actorId.equals(profile.ownerId())) throw failure("PLANNING_PROFILE_FORBIDDEN", "Profile does not belong to the actor", 403);
    }
    private static TradePlanningProfileException failure(String code, String message, int status) {
        return new TradePlanningProfileException(code, message, status);
    }

    public record Values(java.math.BigDecimal riskBudgetAmount, String currency,
                         TradePlanningProfile.EntryType entryType,
                         TradePlanningProfile.StopStrategy stopStrategy,
                         java.math.BigDecimal stopDistancePercent,
                         TradePlanningProfile.TargetStrategy targetStrategy,
                         java.math.BigDecimal targetRiskMultiple,
                         TradePlanningProfile.PlanningHorizon horizon,
                         java.time.Duration validity) { }
}
