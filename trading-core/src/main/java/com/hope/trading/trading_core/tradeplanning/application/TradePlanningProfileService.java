package com.hope.trading.trading_core.tradeplanning.application;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.tradeplanning.domain.TradePlanningProfile;
import com.hope.trading.trading_core.tradeplanning.domain.TradePlanningProfile.PlanningPreferences;
import com.hope.trading.trading_core.tradeplanning.domain.TradePlanningProfile.RiskBudget;
import java.time.Clock;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TradePlanningProfileService {
    private static final BigDecimal PAPER_SIZING_SAFETY_FACTOR = new BigDecimal("0.995");
    private final AccountRepository accounts;
    private final TradePlanningProfileRepository profiles;
    private final BrokerAccountRepository brokerAccounts;
    private final Clock clock;

    public TradePlanningProfileService(AccountRepository accounts,
                                       TradePlanningProfileRepository profiles,
                                       BrokerAccountRepository brokerAccounts,
                                       Clock clock) {
        this.accounts = accounts;
        this.profiles = profiles;
        this.brokerAccounts = brokerAccounts;
        this.clock = clock;
    }



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
        var account = requireAccountOwner(actorId, accountId);
        TradePlanningProfile profile = profiles.findAssigned(accountId)
                .orElseThrow(() -> failure("PLANNING_PROFILE_MISSING", "Account has no effective Trade Planning Profile", 422));
        requireOwner(actorId, profile);
        return adjustPaperBudget(account, profile);
    }

    private TradePlanningProfile append(UUID actorId, UUID id, long version, Values values) {
        RiskBudget budget = new RiskBudget(values.riskBudgetAmount(), values.currency(), id, version);
        PlanningPreferences preferences = new PlanningPreferences(id, version, values.entryType(),
                values.stopStrategy(), values.stopDistancePercent(), values.targetStrategy(),
                values.targetRiskMultiple(), values.horizon(), values.validity());
        return profiles.append(new TradePlanningProfile(id, version, actorId, budget, preferences, clock.instant()));
    }

    private Account requireAccountOwner(UUID actorId, UUID accountId) {
        var account = accounts.findById(accountId)
                .orElseThrow(() -> failure("ACCOUNT_NOT_FOUND", "Account not found", 404));
        if (account.getUser() == null || !actorId.equals(account.getUser().getUserId())) {
            throw failure("ACCOUNT_FORBIDDEN", "Account does not belong to the actor", 403);
        }
        return account;
    }

    private TradePlanningProfile adjustPaperBudget(Account account, TradePlanningProfile profile) {
        if (account.getBrokerAccountId() == null
                || brokerAccounts.findByIdAndOwnerId(account.getBrokerAccountId(), account.getUser().getUserId())
                .map(value -> value.executionMode() != ExecutionMode.PAPER)
                .orElse(true)) {
            return profile;
        }
        BigDecimal currentEquity = account.getEquity();
        BigDecimal baseline = account.getStartingBalance();
        if (baseline == null || baseline.signum() <= 0) baseline = account.getPeakEquity();
        if (currentEquity == null || currentEquity.signum() <= 0
                || baseline == null || baseline.signum() <= 0
                || currentEquity.compareTo(baseline) >= 0) {
            return profile;
        }
        BigDecimal adjustedAmount = profile.riskBudget().amount()
                .multiply(currentEquity, MathContext.DECIMAL128)
                .divide(baseline, MathContext.DECIMAL128)
                .multiply(PAPER_SIZING_SAFETY_FACTOR, MathContext.DECIMAL128)
                .stripTrailingZeros();
        if (adjustedAmount.signum() <= 0) return profile;
        return new TradePlanningProfile(profile.id(), profile.version(), profile.ownerId(),
                new RiskBudget(adjustedAmount, profile.riskBudget().currency(),
                        profile.riskBudget().sourceId(), profile.riskBudget().sourceVersion()),
                profile.preferences(), profile.createdAt());
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
