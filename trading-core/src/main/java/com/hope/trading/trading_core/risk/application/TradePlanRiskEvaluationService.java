package com.hope.trading.trading_core.risk.application;

import com.hope.trading.risk.context.RiskEvaluationContextBuilder;
import com.hope.trading.risk.domain.Money;
import com.hope.trading.risk.domain.ProposedTrade;
import com.hope.trading.risk.domain.RiskEvaluationRequest;
import com.hope.trading.risk.domain.RiskRuleIds;
import com.hope.trading.risk.domain.RiskTypes.EvaluationStatus;
import com.hope.trading.risk.domain.RiskTypes.PolicyAuthority;
import com.hope.trading.risk.domain.RiskTypes.RiskDecision;
import com.hope.trading.risk.domain.RiskTypes.RuleCategory;
import com.hope.trading.risk.domain.RiskTypes.RuleSeverity;
import com.hope.trading.risk.domain.RiskTypes.TradeDirection;
import com.hope.trading.risk.domain.RiskTypes.ValidationMode;
import com.hope.trading.risk.domain.RiskValidationResult;
import com.hope.trading.risk.engine.RiskEngine;
import com.hope.trading.risk.engine.RiskEngines;
import com.hope.trading.risk.policy.EffectiveRiskRuleSet;
import com.hope.trading.risk.policy.RuleConfiguration;
import com.hope.trading.risk.snapshot.AccountSnapshot;
import com.hope.trading.risk.snapshot.MarketSnapshot;
import com.hope.trading.risk.snapshot.PortfolioSnapshot;
import com.hope.trading.risk.snapshot.PositionSnapshot;
import com.hope.trading.risk.snapshot.RuleSetSnapshot;
import com.hope.trading.risk.snapshot.TradingContext;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.risk.application.RiskEvaluationModels.Command;
import com.hope.trading.trading_core.risk.application.RiskEvaluationModels.Reason;
import com.hope.trading.trading_core.risk.application.RiskEvaluationModels.Response;
import com.hope.trading.trading_core.risk.application.RiskEvaluationModels.Trace;
import com.hope.trading.trading_core.risk.application.port.BrokerRiskFactsPort;
import com.hope.trading.trading_core.risk.application.port.MarketValuationPort;
import com.hope.trading.trading_core.risk.application.port.RequiredMarginPort;
import com.hope.trading.trading_core.risk.application.port.TradePlanRiskPort;
import com.hope.trading.trading_core.risk.infrastructure.persistence.RiskPersistence;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TradePlanRiskEvaluationService {
    static final String ENGINE_VERSION = "adr-028-standard-1";
    private static final Pattern SEMVER = Pattern.compile("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?$");
    private static final Pattern CURRENCY = Pattern.compile("^[A-Z][A-Z0-9]{2,15}$");
    private static final Set<String> REQUIRED_RULES = Set.of(RiskRuleIds.MAX_POSITION_RISK,
            RiskRuleIds.MAX_EXPOSURE, RiskRuleIds.DAILY_DRAWDOWN);

    private final AccountRepository accounts;
    private final BrokerAccountRepository brokerAccounts;
    private final TradePlanRiskPort tradePlans;
    private final BrokerRiskFactsPort broker;
    private final MarketValuationPort market;
    private final RequiredMarginPort requiredMargins;
    private final RiskPersistence persistence;
    private final Clock clock;
    private final RiskEngine engine;
    private final RiskAcknowledgmentDeliveryService acknowledgmentDelivery;
    private final TransactionTemplate transactions;

    public TradePlanRiskEvaluationService(AccountRepository accounts, BrokerAccountRepository brokerAccounts,
                                          TradePlanRiskPort tradePlans, BrokerRiskFactsPort broker,
                                           MarketValuationPort market, RequiredMarginPort requiredMargins,
                                           RiskPersistence persistence, Clock clock,
                                          RiskAcknowledgmentDeliveryService acknowledgmentDelivery,
                                          PlatformTransactionManager transactionManager) {
        this.accounts = accounts;
        this.brokerAccounts = brokerAccounts;
        this.tradePlans = tradePlans;
        this.broker = broker;
        this.market = market;
        this.requiredMargins = requiredMargins;
        this.persistence = persistence;
        this.clock = clock;
        this.acknowledgmentDelivery = acknowledgmentDelivery;
        this.transactions = new TransactionTemplate(transactionManager);
        this.engine = RiskEngines.standard(ENGINE_VERSION, clock);
    }

    public Response evaluate(Command command) {
        Response response = transactions.execute(status -> evaluateOfficial(command));
        if (response == null) throw new IllegalStateException("Risk evaluation transaction returned no result");
        try {
            acknowledgmentDelivery.deliver(response.evaluationId());
        } catch (RuntimeException ignored) {
            // Durable scheduled retry remains authoritative when immediate delivery cannot start.
        }
        return response;
    }

    private Response evaluateOfficial(Command command) {
        var stored = persistence.evaluation(command.actorId(), command.idempotencyKey());
        if (stored.isPresent()) {
            var value = stored.get();
            if (!value.tradePlanId().equals(command.tradePlanId())
                    || value.tradePlanVersion() != command.tradePlanVersion()
                    || !value.accountId().equals(command.accountId())) {
                throw new RiskEvaluationException("IDEMPOTENCY_CONFLICT",
                        "Idempotency-Key is already bound to another command", 409);
            }
            return value.response();
        }

        UUID evaluationId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        try {
            return evaluateAvailable(command, evaluationId, correlationId);
        } catch (ContextUnavailable unavailable) {
            return unavailable(command, evaluationId, correlationId, unavailable.code, unavailable.getMessage());
        } catch (RiskEvaluationException commandFailure) {
            throw commandFailure;
        } catch (RuntimeException dependencyFailure) {
            return unavailable(command, evaluationId, correlationId, "DEPENDENCY_UNAVAILABLE",
                    "A required risk-context dependency is unavailable");
        }
    }

    private Response evaluateAvailable(Command command, UUID evaluationId, UUID correlationId) {
        var account = accounts.findById(command.accountId())
                .orElseThrow(() -> new RiskEvaluationException("ACCOUNT_NOT_FOUND", "Account not found", 404));
        if (account.getUser() == null || !command.actorId().equals(account.getUser().getUserId())) {
            throw new RiskEvaluationException("ACCOUNT_FORBIDDEN", "Account does not belong to the actor", 403);
        }

        var configuration = persistence.configuration(command.accountId())
                .orElseThrow(() -> unavailable("ACCOUNT_RISK_CONFIGURATION_MISSING"));
        validateConfiguration(configuration);
        brokerAccounts.findByIdAndOwnerId(configuration.brokerAccountId(), command.actorId())
                .orElseThrow(() -> unavailable("BROKER_ACCOUNT_MAPPING_INVALID"));
        var profile = persistence.assignedProfile(command.accountId())
                .orElseThrow(() -> unavailable("EFFECTIVE_RISK_PROFILE_MISSING"));
        EffectiveRiskRuleSet rules = effectiveRules(profile);

        TradePlanRiskPort.Snapshot plan = tradePlans.load(command.tradePlanId(), command.tradePlanVersion());
        validatePlan(command, plan, configuration.reportingCurrency());
        RiskDay riskDay = RiskDay.containing(command.requestedAt(), configuration.riskTimeZone());
        BrokerRiskFactsPort.Snapshot brokerSnapshot = broker.load(configuration.brokerAccountId(),
                riskDay.startsAt(), riskDay.endsAt());
        requireBroker(brokerSnapshot, riskDay);

        String currency = configuration.reportingCurrency();
        Map<String, BigDecimal> startBalances = reconstructStartBalances(brokerSnapshot, riskDay);
        MarketValuationPort.Snapshot startValuation = market.value(currency, riskDay.startsAt(), List.of(),
                startBalances.keySet().stream().map(asset -> new MarketValuationPort.Asset(asset, asset)).toList());
        requireComplete(startValuation, "RISK_DAY_START_VALUATION_UNAVAILABLE");
        BigDecimal reconstructedDailyStart = valueAssets(startBalances, startValuation);
        if (reconstructedDailyStart.signum() <= 0) throw unavailable("RISK_DAY_START_BALANCE_INVALID");
        RiskPersistence.Baseline baseline = persistence.baseline(command.accountId(), riskDay.date(),
                riskDay.startsAt(), riskDay.endsAt(), currency, reconstructedDailyStart, persistence.write(Map.of(
                        "broker", brokerSnapshot.sourcePayload(), "market", startValuation.sourcePayload(),
                        "balances", startBalances)));
        validateBaseline(baseline, riskDay, currency);
        BigDecimal dailyStart = baseline.amount();

        List<MarketValuationPort.Instrument> instruments = new ArrayList<>();
        for (var position : brokerSnapshot.positions()) instruments.add(new MarketValuationPort.Instrument(
                "position:" + position.positionId(), position.instrument(), position.signedQuantity().signum() > 0
                ? MarketValuationPort.PriceUse.CONSERVATIVE_SELL : MarketValuationPort.PriceUse.CONSERVATIVE_BUY));
        instruments.add(new MarketValuationPort.Instrument("proposed", plan.instrument(),
                "LONG".equals(plan.direction()) ? MarketValuationPort.PriceUse.CONSERVATIVE_BUY
                        : MarketValuationPort.PriceUse.CONSERVATIVE_SELL));
        List<String> currentAssets = new ArrayList<>(brokerSnapshot.assetBalances().keySet());
        currentAssets.add(brokerSnapshot.account().valuationAsset());
        currentAssets.add(plan.sizingCurrency());
        MarketValuationPort.Snapshot current = market.value(currency, brokerSnapshot.observedAt(), instruments,
                currentAssets.stream().distinct().map(asset -> new MarketValuationPort.Asset(asset, asset)).toList());
        requireComplete(current, "CURRENT_MARKET_VALUATION_UNAVAILABLE");

        BigDecimal balance = valueAssets(brokerSnapshot.assetBalances(), current);
        BigDecimal accountRate = assetRate(current, brokerSnapshot.account().valuationAsset());
        BigDecimal equity = positiveOrZero(brokerSnapshot.account().equity(), "BROKER_EQUITY_MISSING").multiply(accountRate);
        BigDecimal margin = positiveOrZero(brokerSnapshot.account().margin(), "BROKER_MARGIN_MISSING").multiply(accountRate);
        ClosedPnl dailyClosed = closedPnl(brokerSnapshot, currency, riskDay);
        BigDecimal dailyClosedPnl = dailyClosed.amount();

        List<PositionSnapshot> positions = positions(brokerSnapshot.positions(), current, currency, accountRate);
        BigDecimal sizingRate = assetRate(current, plan.sizingCurrency());
        BigDecimal notional = positive(plan.notional(), "PLAN_NOTIONAL_INVALID").multiply(sizingRate);
        BigDecimal expectedLoss = positive(plan.expectedMonetaryRisk(), "PLAN_EXPECTED_LOSS_INVALID").multiply(sizingRate);
        RequiredMarginPort.Fact marginFact = requiredMargins.resolve(new RequiredMarginPort.Request(
                        configuration.brokerAccountId(), plan.instrument(), plan.direction(), plan.quantity(),
                        plan.entryIntent().price(), brokerSnapshot.observedAt()))
                .orElseThrow(() -> unavailable("REQUIRED_MARGIN_UNAVAILABLE"));
        BigDecimal requiredMargin = authoritativeMargin(marginFact, currency, brokerSnapshot.observedAt());
        ProposedTrade proposed = new ProposedTrade(plan.tradePlanId(), plan.tradePlanVersion(), plan.instrument(),
                direction(plan.direction()), positive(plan.quantity(), "PLAN_QUANTITY_INVALID"),
                new Money(notional, currency), new Money(expectedLoss, currency), new Money(requiredMargin, currency));

        Map<String, Object> provenance = new LinkedHashMap<>();
        provenance.put("tradePlan", plan.sourcePayload()); provenance.put("broker", brokerSnapshot.sourcePayload());
        provenance.put("riskDay", riskDay); provenance.put("riskDayBaselineVersion", baseline.version());
        provenance.put("riskDayBaselinePayload", baseline.payload());
        provenance.put("candidateStartMarket", startValuation.sourcePayload()); provenance.put("currentMarket", current.sourcePayload());
        provenance.put("closedTradeMarkets", dailyClosed.marketPayloads());
        provenance.put("requiredMargin", marginFact);
        provenance.put("profile", profile);

        long accountVersion = persistence.component(evaluationId, "ACCOUNT",
                "broker:" + brokerSnapshot.sourceVersion(), brokerSnapshot.observedAt(), persistence.write(Map.of(
                        "balance", balance, "equity", equity, "margin", margin, "dailyStartBalance", dailyStart,
                        "dailyClosedPnl", dailyClosedPnl, "provenance", provenance)));
        long portfolioVersion = persistence.component(evaluationId, "PORTFOLIO",
                "broker:" + brokerSnapshot.sourceVersion(), brokerSnapshot.observedAt(), persistence.write(Map.of(
                        "portfolioId", configuration.portfolioId(), "positions", positions,
                        "brokerSource", brokerSnapshot.sourcePayload())));
        long marketVersion = persistence.component(evaluationId, "MARKET",
                "market-data:" + current.sourceVersion(), current.capturedAt(), persistence.write(Map.of(
                        "start", startValuation.sourcePayload(), "current", current.sourcePayload())));
        long ruleVersion = persistence.component(evaluationId, "RULE_SET",
                profile.id() + ":" + profile.semanticVersion(), command.requestedAt(), persistence.write(profile));

        var request = new RiskEvaluationRequest(evaluationId, correlationId, ValidationMode.PRE_TRADE,
                proposed, command.requestedAt());
        var trading = new TradingContext(command.actorId(), command.accountId(), command.requestedAt(),
                riskDay.date().toString(), Map.of("riskTimeZone", configuration.riskTimeZone(),
                "reportingCurrency", currency, "tradePlanContextId", plan.contextId().toString(),
                "tradePlanContextVersion", Long.toString(plan.contextVersion())));
        var accountSnapshot = new AccountSnapshot(command.accountId(), accountVersion, brokerSnapshot.observedAt(),
                new Money(balance, currency), new Money(equity, currency), new Money(margin, currency),
                new Money(dailyStart, currency), new Money(dailyClosedPnl, currency));
        var portfolioSnapshot = new PortfolioSnapshot(configuration.portfolioId(), portfolioVersion,
                brokerSnapshot.observedAt(), positions);
        Map<String, BigDecimal> prices = new HashMap<>();
        current.facts().stream().filter(f -> "INSTRUMENT".equals(f.type()))
                .forEach(f -> prices.put(instrumentName(f.id(), plan, brokerSnapshot), f.value()));
        var marketSnapshot = new MarketSnapshot(marketVersion, current.capturedAt(), prices);
        var ruleSnapshot = new RuleSetSnapshot(ruleVersion, command.requestedAt(), rules);
        var context = new RiskEvaluationContextBuilder().build(request, trading, accountSnapshot,
                portfolioSnapshot, marketSnapshot, ruleSnapshot);
        long contextVersion = persistence.context(evaluationId, command.requestedAt(), persistence.write(Map.of(
                "request", request, "trading", trading, "account", accountSnapshot, "portfolio", portfolioSnapshot,
                "market", marketSnapshot, "rules", ruleSnapshot, "provenance", provenance)));

        RiskValidationResult result = engine.evaluate(context);
        Response response = response(plan, command.accountId(), result, Map.of("account", accountVersion,
                "portfolio", portfolioVersion, "market", marketVersion, "ruleSet", ruleVersion,
                "context", contextVersion));
        persistence.evaluation(evaluationId, command.actorId(), command.idempotencyKey(), command.tradePlanId(),
                command.tradePlanVersion(), command.accountId(), command.requestedAt(),
                result.evaluationStatus().name(), result.decision().map(Enum::name).orElse(null), contextVersion,
                result, response);
        if (result.evaluationStatus() == EvaluationStatus.COMPLETED && result.decision().isPresent()
                && (result.decision().get() == RiskDecision.APPROVED
                || result.decision().get() == RiskDecision.APPROVED_WITH_WARNINGS)) {
            persistence.acknowledgment(evaluationId, plan.tradePlanId(), plan.tradePlanVersion(),
                    result.decision().get().name(), result.evaluatedAt(), clock.instant());
        }
        return response;
    }

    private Response unavailable(Command command, UUID evaluationId, UUID correlationId, String code, String message) {
        Instant now = clock.instant();
        Response response = new Response(evaluationId, command.tradePlanId(), command.tradePlanVersion(),
                command.accountId(), "CONTEXT_UNAVAILABLE", null, false,
                List.of(new Reason(code, null, "BLOCKING", message, Map.of())), List.of(), Map.of(), now,
                new Trace(correlationId, ENGINE_VERSION, Map.of(), Map.of(), Map.of()));
        persistence.evaluation(evaluationId, command.actorId(), command.idempotencyKey(), command.tradePlanId(),
                command.tradePlanVersion(), command.accountId(), command.requestedAt(),
                "CONTEXT_UNAVAILABLE", null, null, response, response);
        return response;
    }

    private EffectiveRiskRuleSet effectiveRules(RiskPersistence.Profile profile) {
        if (profile == null || profile.rules() == null || !semanticVersion(profile.semanticVersion()) || blank(profile.policyId())
                || !semanticVersion(profile.policyVersion()) || blank(profile.provenance())
                || blank(profile.assignmentProvenance()) || !validAuthority(profile.authority())) {
            throw unavailable("EFFECTIVE_RISK_PROFILE_INVALID");
        }
        Set<String> ruleIds = profile.rules().stream().map(RiskPersistence.ProfileRule::ruleId)
                .collect(java.util.stream.Collectors.toSet());
        if (profile.rules().size() != REQUIRED_RULES.size() || !ruleIds.equals(REQUIRED_RULES)) {
            throw unavailable("EFFECTIVE_RISK_PROFILE_INCOMPLETE");
        }
        List<RuleConfiguration> rules = profile.rules().stream().map(rule -> {
            if (blank(rule.provenance()) || !semanticVersion(rule.ruleVersion())
                    || rule.maximumRatio() == null || rule.maximumRatio().signum() <= 0
                    || rule.priority() < 0 || !validRuleVocabulary(rule)) {
                throw unavailable("EFFECTIVE_RISK_PROFILE_INVALID");
            }
            return new RuleConfiguration(rule.ruleId(), rule.ruleVersion(), RuleCategory.valueOf(rule.category()),
                    RuleSeverity.valueOf(rule.severity()), rule.priority(), Map.of("maximumRatio", rule.maximumRatio()));
        }).toList();
        return new EffectiveRiskRuleSet(rules, Map.of(profile.policyId(), profile.policyVersion()));
    }

    private boolean validRuleVocabulary(RiskPersistence.ProfileRule rule) {
        try {
            RuleSeverity.valueOf(rule.severity());
            RuleCategory category = RuleCategory.valueOf(rule.category());
            return category == switch (rule.ruleId()) {
                case RiskRuleIds.MAX_POSITION_RISK -> RuleCategory.POSITION;
                case RiskRuleIds.MAX_EXPOSURE -> RuleCategory.PORTFOLIO;
                case RiskRuleIds.DAILY_DRAWDOWN -> RuleCategory.ACCOUNT;
                default -> null;
            };
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private boolean validAuthority(String authority) {
        try { PolicyAuthority.valueOf(authority); return true; }
        catch (RuntimeException invalid) { return false; }
    }

    private static boolean semanticVersion(String value) {
        return value != null && SEMVER.matcher(value).matches();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalizedCurrency(String value) {
        return value.strip().toUpperCase(java.util.Locale.ROOT);
    }

    private void validateConfiguration(RiskPersistence.AccountConfiguration configuration) {
        try { ZoneId.of(configuration.riskTimeZone()); }
        catch (RuntimeException invalid) { throw unavailable("RISK_TIME_ZONE_INVALID"); }
        if (!CURRENCY.matcher(configuration.reportingCurrency()).matches()) {
            throw unavailable("REPORTING_CURRENCY_INVALID");
        }
    }

    private void validatePlan(Command command, TradePlanRiskPort.Snapshot plan, String reportingCurrency) {
        if (!command.tradePlanId().equals(plan.tradePlanId()) || command.tradePlanVersion() != plan.tradePlanVersion())
            throw new RiskEvaluationException("TRADE_PLAN_VERSION_MISMATCH", "Trade Plan version mismatch", 409);
        if (!"ACCEPTED".equals(plan.status())) throw new RiskEvaluationException("TRADE_PLAN_NOT_ACCEPTED", "Trade Plan is not accepted", 422);
        if (!command.actorId().equals(plan.ownerId()) || !command.accountId().equals(plan.tradingAccountId()))
            throw new RiskEvaluationException("TRADE_PLAN_FORBIDDEN", "Trade Plan ownership does not match account", 403);
        if (plan.sourcePayload() == null || plan.contextId() == null || plan.contextVersion() < 1)
            throw unavailable("TRADE_PLAN_PROVENANCE_INCOMPLETE");
        if (blank(plan.accountCurrency())
                || !normalizedCurrency(plan.accountCurrency()).equals(normalizedCurrency(reportingCurrency)))
            throw unavailable("TRADE_PLAN_ACCOUNT_CURRENCY_MISMATCH");
        if (blank(plan.sizingCurrency())
                || !normalizedCurrency(plan.sizingCurrency()).equals(normalizedCurrency(reportingCurrency)))
            throw unavailable("TRADE_PLAN_SIZING_CURRENCY_MISMATCH");
    }

    private void requireBroker(BrokerRiskFactsPort.Snapshot snapshot, RiskDay riskDay) {
        if (!snapshot.complete() || snapshot.account() == null || snapshot.sourceVersion() < 1
                || snapshot.observedAt() == null || !riskDay.contains(snapshot.observedAt())
                || snapshot.assetBalances() == null || snapshot.positions() == null
                || snapshot.closedTrades() == null || snapshot.ledgerEntries() == null) {
            throw unavailable("BROKER_RISK_FACTS_INCOMPLETE");
        }
        if (snapshot.closedTrades().stream().anyMatch(t -> t == null || t.closedAt() == null
                || !riskDay.contains(t.closedAt()) || t.closedAt().isAfter(snapshot.observedAt()))
                || snapshot.ledgerEntries().stream().anyMatch(e -> e == null || e.occurredAt() == null
                || !riskDay.contains(e.occurredAt()) || e.occurredAt().isAfter(snapshot.observedAt()))) {
            throw unavailable("BROKER_RISK_INTERVAL_INVALID");
        }
    }

    static Map<String, BigDecimal> reconstructStartBalances(BrokerRiskFactsPort.Snapshot snapshot, RiskDay day) {
        Map<String, BigDecimal> balances = new LinkedHashMap<>(snapshot.assetBalances());
        Map<String, List<BrokerRiskFactsPort.LedgerEntry>> entriesByAsset = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (var entry : snapshot.ledgerEntries()) {
            if (entry == null || entry.occurredAt() == null || !day.contains(entry.occurredAt())
                    || entry.occurredAt().isAfter(snapshot.observedAt()) || blank(entry.asset())
                    || entry.amount() == null || entry.fee() == null || entry.balance() == null)
                throw unavailable("LEDGER_INTERVAL_INCOMPLETE");
            if (entry.providerLedgerReference() == null || entry.providerLedgerReference().isBlank())
                throw unavailable("LEDGER_PROVENANCE_INCOMPLETE");
            if (entry.fee().signum() < 0) throw unavailable("LEDGER_FEE_INVALID");
            entriesByAsset.computeIfAbsent(entry.asset(), ignored -> new ArrayList<>()).add(entry);
        }
        for (var assetEntries : entriesByAsset.entrySet()) {
            String balanceAsset = balances.keySet().stream().filter(asset -> asset.equalsIgnoreCase(assetEntries.getKey()))
                    .findFirst().orElseThrow(() -> unavailable("LEDGER_ASSET_BALANCE_MISSING"));
            List<BrokerRiskFactsPort.LedgerEntry> entries = assetEntries.getValue();
            entries.sort(Comparator.comparing(BrokerRiskFactsPort.LedgerEntry::occurredAt)
                    .thenComparing(BrokerRiskFactsPort.LedgerEntry::providerLedgerReference));
            for (int index = 1; index < entries.size(); index++) {
                var previous = entries.get(index - 1);
                var current = entries.get(index);
                BigDecimal expected = previous.balance().add(current.amount()).subtract(current.fee());
                if (expected.compareTo(current.balance()) != 0)
                    throw unavailable("LEDGER_RUNNING_BALANCE_MISMATCH");
            }
            var last = entries.get(entries.size() - 1);
            if (balances.get(balanceAsset) == null || balances.get(balanceAsset).compareTo(last.balance()) != 0)
                throw unavailable("LEDGER_TERMINAL_BALANCE_MISMATCH");
            var first = entries.get(0);
            balances.put(balanceAsset, first.balance().subtract(first.amount()).add(first.fee()));
        }
        return Map.copyOf(balances);
    }

    private ClosedPnl closedPnl(BrokerRiskFactsPort.Snapshot snapshot, String currency, RiskDay riskDay) {
        BigDecimal total = BigDecimal.ZERO;
        List<String> payloads = new ArrayList<>();
        for (var trade : snapshot.closedTrades()) {
            if (!riskDay.contains(trade.closedAt()) || trade.realizedPnl() == null || trade.fee() == null
                    || trade.fee().signum() < 0 || blank(trade.settlementAsset()))
                throw unavailable("CLOSED_TRADE_FACT_INCOMPLETE");
            MarketValuationPort.Snapshot valuation = market.value(currency, trade.closedAt(), List.of(),
                    List.of(new MarketValuationPort.Asset("pnl", trade.settlementAsset()),
                            new MarketValuationPort.Asset("fee", trade.settlementAsset())));
            requireComplete(valuation, "CLOSED_TRADE_CONVERSION_UNAVAILABLE");
            payloads.add(valuation.sourcePayload());
            total = total.add(trade.realizedPnl().multiply(fact(valuation, "pnl").value()))
                    .subtract(trade.fee().multiply(fact(valuation, "fee").value()));
        }
        return new ClosedPnl(total, List.copyOf(payloads));
    }

    private List<PositionSnapshot> positions(List<BrokerRiskFactsPort.Position> brokerPositions,
                                             MarketValuationPort.Snapshot valuation, String currency,
                                             BigDecimal marginConversionRate) {
        List<PositionSnapshot> result = new ArrayList<>();
        for (var position : brokerPositions) {
            if (position.positionId() == null || position.signedQuantity() == null
                    || position.signedQuantity().signum() == 0 || position.entryPrice() == null
                    || position.margin() == null || position.protectedQuantity() == null
                    || position.protectiveStops() == null
                    || position.protectedQuantity().compareTo(position.signedQuantity().abs()) != 0) {
                throw unavailable("POSITION_PROTECTION_INCOMPLETE");
            }
            if (position.protectiveStops().stream().anyMatch(stop -> stop == null || stop.quantity() == null
                    || stop.quantity().signum() <= 0 || stop.stopPrice() == null || stop.stopPrice().signum() <= 0)) {
                throw unavailable("POSITION_PROTECTION_INCOMPLETE");
            }
            BigDecimal protectedTotal = position.protectiveStops().stream().map(BrokerRiskFactsPort.Stop::quantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (protectedTotal.compareTo(position.signedQuantity().abs()) != 0)
                throw unavailable("POSITION_PROTECTION_AMBIGUOUS");
            var price = fact(valuation, "position:" + position.positionId());
            if (price.value() == null || price.value().signum() <= 0
                    || price.sourcePrice() == null || price.sourcePrice().signum() <= 0
                    || price.quoteToReportingRate() == null || price.quoteToReportingRate().signum() <= 0
                    || blank(price.sourceProvenance())
                    || price.sourcePrice().multiply(price.quoteToReportingRate()).compareTo(price.value()) != 0)
                throw unavailable("POSITION_PRICE_PROVENANCE_INCOMPLETE");
            BigDecimal conversion = price.quoteToReportingRate();
            BigDecimal loss = BigDecimal.ZERO;
            for (var stop : position.protectiveStops()) {
                BigDecimal distance = position.signedQuantity().signum() > 0
                        ? price.sourcePrice().subtract(stop.stopPrice())
                        : stop.stopPrice().subtract(price.sourcePrice());
                if (distance.signum() <= 0) throw unavailable("POSITION_STOP_NOT_PROTECTIVE");
                loss = loss.add(distance.multiply(stop.quantity()).multiply(conversion));
            }
            result.add(new PositionSnapshot(position.positionId(), position.instrument(), position.signedQuantity(),
                    new Money(position.signedQuantity().abs().multiply(price.value()), currency),
                    new Money(loss, currency), new Money(position.margin().multiply(marginConversionRate), currency)));
        }
        return List.copyOf(result);
    }

    private static BigDecimal valueAssets(Map<String, BigDecimal> balances, MarketValuationPort.Snapshot valuation) {
        BigDecimal result = BigDecimal.ZERO;
        for (var balance : balances.entrySet()) result = result.add(balance.getValue().multiply(assetRate(valuation, balance.getKey())));
        return result;
    }

    private static BigDecimal assetRate(MarketValuationPort.Snapshot valuation, String asset) {
        return valuation.facts().stream().filter(f -> "ASSET".equals(f.type()) && asset.equalsIgnoreCase(f.asset()))
                .map(MarketValuationPort.Fact::value).findFirst().orElseThrow(() -> unavailable("FX_UNAVAILABLE"));
    }

    private static MarketValuationPort.Fact fact(MarketValuationPort.Snapshot valuation, String id) {
        return valuation.facts().stream().filter(f -> id.equals(f.id()) && "AVAILABLE".equals(f.status()) && f.value() != null)
                .findFirst().orElseThrow(() -> unavailable("VALUATION_FACT_UNAVAILABLE"));
    }

    private static void requireComplete(MarketValuationPort.Snapshot snapshot, String code) {
        if (snapshot == null || !snapshot.complete() || snapshot.sourceVersion() < 1
                || snapshot.facts().stream().anyMatch(f -> !"AVAILABLE".equals(f.status()) || f.value() == null))
            throw unavailable(code);
    }

    private static void validateBaseline(RiskPersistence.Baseline baseline, RiskDay riskDay, String currency) {
        if (baseline == null || baseline.version() < 1 || baseline.amount() == null
                || baseline.amount().signum() <= 0 || !currency.equals(baseline.reportingCurrency())
                || !riskDay.startsAt().equals(baseline.startsAt()) || !riskDay.endsAt().equals(baseline.endsAt())
                || baseline.payloadSchemaVersion() < 1 || blank(baseline.payload())) {
            throw unavailable("RISK_DAY_BASELINE_INVALID");
        }
    }

    private static String instrumentName(String factId, TradePlanRiskPort.Snapshot plan,
                                         BrokerRiskFactsPort.Snapshot broker) {
        if ("proposed".equals(factId)) return plan.instrument().toUpperCase();
        if (factId.startsWith("position:")) {
            UUID id = UUID.fromString(factId.substring("position:".length()));
            return broker.positions().stream().filter(p -> id.equals(p.positionId())).findFirst()
                    .orElseThrow(() -> unavailable("POSITION_MARKET_FACT_UNKNOWN")).instrument().toUpperCase();
        }
        throw unavailable("MARKET_FACT_UNKNOWN");
    }

    private Response response(TradePlanRiskPort.Snapshot plan, UUID accountId, RiskValidationResult result,
                              Map<String, Long> versions) {
        List<Reason> violations = result.violations().stream().map(this::reason).toList();
        List<Reason> warnings = result.warnings().stream().map(this::reason).toList();
        Map<String, BigDecimal> metrics = Map.of(
                "positionRiskRatio", result.globalMetrics().positionRiskRatio().value(),
                "exposureRatio", result.globalMetrics().exposureRatio().value(),
                "dailyDrawdownRatio", result.globalMetrics().dailyDrawdownRatio().value());
        var trace = result.trace();
        return new Response(trace.evaluationId(), plan.tradePlanId(), plan.tradePlanVersion(), accountId,
                result.evaluationStatus().name(), result.decision().map(Enum::name).orElse(null),
                result.decision().filter(d -> d == RiskDecision.APPROVED || d == RiskDecision.APPROVED_WITH_WARNINGS).isPresent(),
                violations, warnings, metrics, result.evaluatedAt(), new Trace(trace.correlationId(),
                trace.engineVersion(), trace.policyVersions(), trace.ruleVersions(), versions));
    }

    private Reason reason(com.hope.trading.risk.rule.RiskRuleResult result) {
        return new Reason(result.ruleId(), result.ruleVersion(), result.severity().name(),
                result.explanation().code(), result.metrics());
    }

    private static TradeDirection direction(String value) {
        try { return TradeDirection.valueOf(value); }
        catch (RuntimeException invalid) { throw unavailable("PLAN_DIRECTION_INVALID"); }
    }
    private static BigDecimal positive(BigDecimal value, String code) {
        if (value == null || value.signum() <= 0) throw unavailable(code); return value;
    }
    private static BigDecimal positiveOrZero(BigDecimal value, String code) {
        if (value == null || value.signum() < 0) throw unavailable(code); return value;
    }
    private static BigDecimal authoritativeMargin(RequiredMarginPort.Fact fact, String currency, Instant asOf) {
        if (fact.amount() == null || fact.amount().signum() <= 0 || blank(fact.currency())
                || !normalizedCurrency(fact.currency()).equals(normalizedCurrency(currency))
                || blank(fact.sourceId()) || fact.sourceVersion() < 1 || fact.observedAt() == null
                || fact.observedAt().isAfter(asOf)) {
            throw unavailable("REQUIRED_MARGIN_INVALID");
        }
        return fact.amount();
    }
    private static ContextUnavailable unavailable(String code) { return new ContextUnavailable(code); }
    private static final class ContextUnavailable extends RuntimeException {
        private final String code;
        private ContextUnavailable(String code) { super(code); this.code = code; }
    }
    private record ClosedPnl(BigDecimal amount, List<String> marketPayloads) { }
}
