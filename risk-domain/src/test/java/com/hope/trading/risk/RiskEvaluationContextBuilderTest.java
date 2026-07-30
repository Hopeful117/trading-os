package com.hope.trading.risk;

import com.hope.trading.risk.context.RiskEvaluationContextBuilder;
import com.hope.trading.risk.domain.*;
import com.hope.trading.risk.snapshot.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static com.hope.trading.risk.RiskFixture.*;
import static com.hope.trading.risk.domain.RiskTypes.*;
import static org.junit.jupiter.api.Assertions.*;

class RiskEvaluationContextBuilderTest {
    @Test void contextHasNoPublicConstructor() {
        assertEquals(0, Arrays.stream(
                com.hope.trading.risk.context.RiskEvaluationContext.class
                        .getConstructors()).count());
    }

    @Test void rejectsTradeWithoutCoherentMarketSnapshot() {
        var request = new RiskEvaluationRequest(UUID.randomUUID(), UUID.randomUUID(),
                ValidationMode.PRE_TRADE,
                new ProposedTrade(UUID.randomUUID(), 1, "EURUSD",
                    TradeDirection.LONG, BigDecimal.ONE, usd("10"), usd("1"), usd("1")),
                NOW);
        var account = new AccountSnapshot(ACCOUNT_ID, 1, NOW, usd("100"),
                usd("100"), usd("0"), usd("100"), usd("0"));
        assertThrows(IllegalArgumentException.class, () ->
                new RiskEvaluationContextBuilder().build(request,
                    new TradingContext(UUID.randomUUID(), ACCOUNT_ID, NOW, "OPEN", Map.of()),
                    account, new PortfolioSnapshot(UUID.randomUUID(), 1, NOW, List.of()),
                    new MarketSnapshot(1, NOW, Map.of()),
                    new RuleSetSnapshot(1, NOW, rules())));
    }
}
