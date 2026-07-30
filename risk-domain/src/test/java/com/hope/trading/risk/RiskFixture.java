package com.hope.trading.risk;

import com.hope.trading.risk.context.*;
import com.hope.trading.risk.domain.*;
import com.hope.trading.risk.policy.*;
import com.hope.trading.risk.snapshot.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static com.hope.trading.risk.domain.RiskTypes.*;

final class RiskFixture {
    static final Instant NOW = Instant.parse("2026-07-30T12:00:00Z");
    static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static Money usd(String amount) { return new Money(new BigDecimal(amount), "USD"); }
    static RuleConfiguration rule(String id, RuleCategory category, RuleSeverity severity,
                                  String maximum) {
        return new RuleConfiguration(id, "1.0", category, severity, 10,
                Map.of("maximumRatio", new BigDecimal(maximum)));
    }
    static EffectiveRiskRuleSet rules(RuleConfiguration... configurations) {
        return new EffectiveRiskRuleSet(List.of(configurations), Map.of("platform", "1"));
    }
    static RiskEvaluationContext context(EffectiveRiskRuleSet rules) {
        var request = new RiskEvaluationRequest(
                UUID.fromString("00000000-0000-0000-0000-000000000010"),
                UUID.fromString("00000000-0000-0000-0000-000000000011"),
                ValidationMode.PRE_TRADE,
                new ProposedTrade(
                        UUID.fromString("00000000-0000-0000-0000-000000000012"), 1,
                        "EURUSD", TradeDirection.LONG, new BigDecimal("1000"),
                        usd("2000"), usd("100"), usd("200")), NOW);
        var account = new AccountSnapshot(ACCOUNT_ID, 3, NOW, usd("10000"),
                usd("9800"), usd("500"), usd("10000"), usd("-100"));
        var position = new PositionSnapshot(
                UUID.fromString("00000000-0000-0000-0000-000000000013"),
                "AAPL", BigDecimal.TEN, usd("1000"), usd("50"), usd("100"));
        var portfolio = new PortfolioSnapshot(
                UUID.fromString("00000000-0000-0000-0000-000000000014"),
                2, NOW, List.of(position));
        return new RiskEvaluationContextBuilder().build(request,
                new TradingContext(
                        UUID.fromString("00000000-0000-0000-0000-000000000015"),
                        ACCOUNT_ID, NOW, "NEW_YORK", Map.of()),
                account, portfolio, new MarketSnapshot(4, NOW,
                    Map.of("EURUSD", new BigDecimal("1.10"))),
                new RuleSetSnapshot(1, NOW, rules));
    }
}
