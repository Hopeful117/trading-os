package com.hope.trading.trading_core.risk.application;

import com.hope.trading.risk.context.RiskEvaluationContextBuilder;
import com.hope.trading.risk.domain.Money;
import com.hope.trading.risk.domain.RiskEvaluationRequest;
import com.hope.trading.risk.domain.RiskTypes.ValidationMode;
import com.hope.trading.risk.domain.RiskValidationResult;
import com.hope.trading.risk.engine.RiskEngine;
import com.hope.trading.risk.engine.RiskEngines;
import com.hope.trading.risk.policy.EffectiveRiskRuleSet;
import com.hope.trading.risk.snapshot.AccountSnapshot;
import com.hope.trading.risk.snapshot.DailyRiskBaseline;
import com.hope.trading.risk.snapshot.MarketSnapshot;
import com.hope.trading.risk.snapshot.PortfolioSnapshot;
import com.hope.trading.risk.snapshot.PositionSnapshot;
import com.hope.trading.risk.snapshot.RuleSetSnapshot;
import com.hope.trading.risk.snapshot.TradingContext;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.challenge.domain.ChallengeInstance;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.risk.application.port.RiskFactsProvider;
import com.hope.trading.trading_core.risk.infrastructure.persistence.RiskPersistence;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Builds observed account context and delegates all risk calculations to Risk Domain. */
@Service
public class AccountRiskMonitoringService {
    private static final String ENGINE_VERSION = TradePlanRiskEvaluationService.ENGINE_VERSION;

    private final AccountRepositoryAdapter accounts;
    private final BrokerAccountRepository brokerAccounts;
    private final RiskFactsProvider facts;
    private final RiskPersistence persistence;
    private final RiskProfileValidator profileValidator;
    private final Clock clock;
    private final RiskEngine engine;

    public AccountRiskMonitoringService(com.hope.trading.trading_core.repository.AccountRepository accounts,
                                        BrokerAccountRepository brokerAccounts, RiskFactsProvider facts,
                                        RiskPersistence persistence, RiskProfileValidator profileValidator,
                                        Clock clock) {
        this.accounts = accounts::findById;
        this.brokerAccounts = brokerAccounts;
        this.facts = facts;
        this.persistence = persistence;
        this.profileValidator = profileValidator;
        this.clock = clock;
        this.engine = RiskEngines.standard(ENGINE_VERSION, clock);
    }

    public Evaluation evaluate(ChallengeInstance challenge, Instant requestedAt) {
        Account account = accounts.find(challenge.accountId())
                .orElseThrow(() -> new IllegalArgumentException("Account does not exist"));
        return evaluate(challenge, account, requestedAt);
    }

    public Evaluation evaluate(ChallengeInstance challenge, Account account, Instant requestedAt) {
        if (account.getBrokerAccountId() == null || account.getUser() == null) {
            throw new IllegalStateException("Account risk identity is incomplete");
        }
        BrokerAccount broker = brokerAccounts.findById(account.getBrokerAccountId())
                .orElseThrow(() -> new IllegalStateException("Broker account does not exist"));
        RiskPersistence.AccountConfiguration configuration = persistence.configuration(account.getAccountId())
                .orElseThrow(() -> new IllegalStateException("Account risk configuration is missing"));
        if (!challenge.capitalCurrency().equalsIgnoreCase(configuration.reportingCurrency())) {
            throw new IllegalStateException("Challenge and Risk reporting currencies differ");
        }
        RiskPersistence.Profile profile = persistence.profile(challenge.riskPolicyId(), challenge.riskPolicyVersion())
                .orElseThrow(() -> new IllegalStateException("Challenge Risk Policy does not exist"));
        EffectiveRiskRuleSet validatedRules = profileValidator.validate(profile, false);
        EffectiveRiskRuleSet rules = new EffectiveRiskRuleSet(validatedRules.rules(),
                Map.of(challenge.riskPolicyId().toString(), challenge.riskPolicyVersion()));
        RiskDay day = RiskDay.containing(requestedAt, configuration.riskTimeZone(),
                LocalTime.parse(configuration.riskDayResetTime()));
        RiskFactsProvider.Snapshot snapshot = facts.load(account, broker, broker.id(), day.startsAt(), day.endsAt());
        if (!snapshot.complete() || snapshot.account() == null || snapshot.observedAt() == null
                || snapshot.account().balance() == null || snapshot.account().equity() == null) {
            throw new IllegalStateException("PAPER account Risk facts are incomplete");
        }

        String currency = configuration.reportingCurrency();
        BigDecimal balance = snapshot.account().balance();
        BigDecimal equity = snapshot.account().equity();
        BigDecimal starting = account.getStartingBalance();
        if (starting == null || starting.signum() <= 0) {
            throw new IllegalStateException("Account starting balance is unavailable");
        }
        UUID evaluationId = UUID.randomUUID();
        Instant capturedAt = snapshot.observedAt();
        RiskPersistence.Baseline baseline = persistence.baseline(account.getAccountId(), day.date(),
                day.startsAt(), day.endsAt(), currency, starting,
                persistence.write(Map.of("source", "ACCOUNT_STARTING_BALANCE",
                        "accountVersion", account.getVersion(), "riskDay", day.date().toString())));
        long accountVersion = persistence.component(evaluationId, "ACCOUNT",
                "paper-account:" + Math.max(1, snapshot.sourceVersion()), capturedAt,
                persistence.write(snapshot.account()));
        List<PositionSnapshot> positions = positions(snapshot, currency);
        long portfolioVersion = persistence.component(evaluationId, "PORTFOLIO",
                "paper-account:" + Math.max(1, snapshot.sourceVersion()), capturedAt,
                persistence.write(positions));
        long marketVersion = persistence.component(evaluationId, "MARKET", "PAPER_ACCOUNT_STATE",
                capturedAt, persistence.write(Map.of("source", "PAPER_ACCOUNT_STATE")));
        long ruleVersion = persistence.component(evaluationId, "RULE_SET",
                profile.id() + ":" + profile.semanticVersion(), requestedAt, persistence.write(profile));

        BigDecimal dailyClosedPnl = snapshot.closedTrades().stream()
                .map(RiskFactsProvider.ClosedTrade::realizedPnl)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var request = new RiskEvaluationRequest(evaluationId, UUID.randomUUID(),
                ValidationMode.ACCOUNT_MONITORING, null, requestedAt);
        var trading = new TradingContext(account.getUser().getUserId(), account.getAccountId(), requestedAt,
                day.date().toString(), Map.of("riskTimeZone", configuration.riskTimeZone(),
                        "reportingCurrency", currency, "source", "PAPER_ACCOUNT"));
        var accountSnapshot = new AccountSnapshot(account.getAccountId(), Math.max(1, accountVersion), capturedAt,
                new Money(balance, currency), new Money(equity, currency),
                new Money(nonNegative(snapshot.account().margin()), currency),
                java.util.Optional.of(new Money(starting, currency)),
                new DailyRiskBaseline(new Money(baseline.amount(), currency), baseline.startsAt(),
                        "RISK_DAY_BASELINE", Map.of("baselineVersion", Long.toString(baseline.version()),
                        "riskDay", day.date().toString())),
                new Money(dailyClosedPnl, currency));
        var portfolioSnapshot = new PortfolioSnapshot(configuration.portfolioId(), Math.max(1, portfolioVersion),
                capturedAt, positions);
        var marketSnapshot = new MarketSnapshot(Math.max(1, marketVersion), capturedAt, Map.of());
        var ruleSnapshot = new RuleSetSnapshot(Math.max(1, ruleVersion), requestedAt, rules);
        var context = new RiskEvaluationContextBuilder().build(request, trading, accountSnapshot,
                portfolioSnapshot, marketSnapshot, ruleSnapshot);
        long contextVersion = persistence.context(evaluationId, requestedAt,
                persistence.write(Map.of("request", request, "trading", trading,
                        "account", accountSnapshot, "portfolio", portfolioSnapshot,
                        "market", marketSnapshot, "rules", ruleSnapshot)));
        RiskValidationResult result = engine.evaluate(context);
        persistence.accountMonitoringEvaluation(evaluationId, account.getUser().getUserId(),
                "challenge:" + challenge.id() + ":account:" + account.getVersion(), account.getAccountId(),
                requestedAt, result.evaluationStatus().name(),
                result.decision().map(Enum::name).orElse(null), contextVersion, result);
        return new Evaluation(evaluationId, result, baseline.amount(), contextVersion);
    }

    private List<PositionSnapshot> positions(RiskFactsProvider.Snapshot snapshot, String currency) {
        List<PositionSnapshot> result = new ArrayList<>();
        for (RiskFactsProvider.Position position : snapshot.positions()) {
            if (position.positionId() == null || position.instrument() == null
                    || position.signedQuantity() == null || position.signedQuantity().signum() == 0
                    || position.marketValue() == null || position.marketValue().signum() < 0) {
                throw new IllegalStateException("PAPER position Risk facts are incomplete");
            }
            result.add(new PositionSnapshot(position.positionId(), position.instrument(), position.signedQuantity(),
                    new Money(position.marketValue(), currency), null,
                    new Money(nonNegative(position.margin()), currency),
                    position.protectionStatus() == null
                            ? com.hope.trading.risk.domain.RiskTypes.ProtectionStatus.UNKNOWN
                            : position.protectionStatus()));
        }
        return List.copyOf(result);
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        return value == null || value.signum() < 0 ? BigDecimal.ZERO : value;
    }

    public record Evaluation(UUID evaluationId, RiskValidationResult result,
                             BigDecimal dailyReferenceBalance, long contextVersion) { }

    private interface AccountRepositoryAdapter {
        java.util.Optional<Account> find(UUID accountId);
    }
}
