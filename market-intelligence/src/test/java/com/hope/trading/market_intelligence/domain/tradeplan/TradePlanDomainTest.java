package com.hope.trading.market_intelligence.domain.tradeplan;

import com.hope.trading.market_intelligence.application.tradeplan.TradePlanTestFixtures;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class TradePlanDomainTest {
    @Test
    void valueObjectsValidatePricesSizingAndEquality() {
        assertThat(new RiskReward(BigDecimal.valueOf(2)))
                .isEqualTo(new RiskReward(new BigDecimal("2.00")));
        assertThatThrownBy(() -> new StopLoss(BigDecimal.ZERO, "invalid"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PositionSizing(
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, "EUR"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TakeProfit(
                BigDecimal.TEN, BigDecimal.valueOf(101)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void executionRejectsContradictoryLongPrices() {
        assertThatThrownBy(() -> new ExecutionParameters(
                "BTC/EUR", TradeDirection.LONG,
                new EntryStrategy(EntryType.LIMIT, BigDecimal.valueOf(100), Set.of()),
                new StopLoss(BigDecimal.valueOf(101), "wrong"),
                List.of(new TakeProfit(BigDecimal.valueOf(110), BigDecimal.valueOf(100))),
                new PositionSizing(BigDecimal.ONE, BigDecimal.valueOf(100),
                        BigDecimal.ONE, "EUR"),
                new RiskReward(BigDecimal.TEN),
                new PlanExpiration(TradePlanTestFixtures.NOW.plusSeconds(60), "fixed"),
                Set.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void contextSnapshotDefensivelyCopiesAndExposesExactReference() {
        UUID id = UUID.randomUUID(); UUID owner = UUID.randomUUID();
        Map<String, String> preferences = new HashMap<>(Map.of("type", "LIMIT"));
        TradingContext context = new TradingContext(
                id, 2, TradePlanTestFixtures.NOW, owner, UUID.randomUUID(), "eur",
                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE, "LOW", "RULE",
                Map.of(), preferences);
        preferences.clear();
        assertThat(context.executionPreferences()).containsEntry("type", "LIMIT");
        assertThat(context.reference()).isEqualTo(new TradingContextReference(
                id, 2, TradePlanTestFixtures.NOW));
    }
}
