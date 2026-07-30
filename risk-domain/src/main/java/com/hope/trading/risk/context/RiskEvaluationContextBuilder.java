package com.hope.trading.risk.context;

import com.hope.trading.risk.domain.RiskEvaluationRequest;
import com.hope.trading.risk.snapshot.*;
import java.util.Objects;

/** Assembles and validates one coherent immutable business snapshot only. */
public final class RiskEvaluationContextBuilder {
    public RiskEvaluationContext build(
            RiskEvaluationRequest request, TradingContext trading, AccountSnapshot account,
            PortfolioSnapshot portfolio, MarketSnapshot market, RuleSetSnapshot ruleSet
    ) {
        Objects.requireNonNull(request); Objects.requireNonNull(trading);
        Objects.requireNonNull(account); Objects.requireNonNull(portfolio);
        Objects.requireNonNull(market); Objects.requireNonNull(ruleSet);
        if (!trading.accountId().equals(account.accountId())) {
            throw new IllegalArgumentException("Trading and account snapshots are inconsistent");
        }
        if (!sameCurrency(account, portfolio)) {
            throw new IllegalArgumentException("Portfolio monetary values must use account currency");
        }
        if (request.proposedTrade() != null) {
            var trade = request.proposedTrade();
            String currency = account.balance().currency();
            if (!currency.equals(trade.notional().currency())
                    || !currency.equals(trade.expectedLossAtStop().currency())
                    || !currency.equals(trade.marginRequired().currency())) {
                throw new IllegalArgumentException("Trade monetary values must use account currency");
            }
            if (!market.prices().containsKey(trade.instrument())) {
                throw new IllegalArgumentException("Missing market price for proposed instrument");
            }
        }
        return new RiskEvaluationContext(request, trading, account, portfolio, market, ruleSet);
    }

    private boolean sameCurrency(AccountSnapshot account, PortfolioSnapshot portfolio) {
        String currency = account.balance().currency();
        return portfolio.positions().stream().allMatch(p ->
                currency.equals(p.marketValue().currency())
                        && currency.equals(p.lossAtStop().currency())
                        && currency.equals(p.marginUsed().currency()));
    }
}
