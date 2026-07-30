package com.hope.trading.risk.context;

import com.hope.trading.risk.domain.RiskEvaluationRequest;
import com.hope.trading.risk.policy.EffectiveRiskRuleSet;
import com.hope.trading.risk.snapshot.*;
import java.util.Objects;

/**
 * Immutable coherent input snapshot. Construction is intentionally restricted
 * to the builder in this package.
 */
public final class RiskEvaluationContext {
    private final RiskEvaluationRequest request;
    private final TradingContext trading;
    private final AccountSnapshot account;
    private final PortfolioSnapshot portfolio;
    private final MarketSnapshot market;
    private final RuleSetSnapshot ruleSet;

    RiskEvaluationContext(
            RiskEvaluationRequest request, TradingContext trading,
            AccountSnapshot account, PortfolioSnapshot portfolio,
            MarketSnapshot market, RuleSetSnapshot ruleSet
    ) {
        this.request = Objects.requireNonNull(request);
        this.trading = Objects.requireNonNull(trading);
        this.account = Objects.requireNonNull(account);
        this.portfolio = Objects.requireNonNull(portfolio);
        this.market = Objects.requireNonNull(market);
        this.ruleSet = Objects.requireNonNull(ruleSet);
    }

    public RiskEvaluationRequest request() { return request; }
    public TradingContext trading() { return trading; }
    public AccountSnapshot account() { return account; }
    public PortfolioSnapshot portfolio() { return portfolio; }
    public MarketSnapshot market() { return market; }
    public RuleSetSnapshot ruleSetSnapshot() { return ruleSet; }
    public EffectiveRiskRuleSet ruleSet() { return ruleSet.effectiveRuleSet(); }
}
