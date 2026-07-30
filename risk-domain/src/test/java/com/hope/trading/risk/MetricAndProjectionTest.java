package com.hope.trading.risk;

import com.hope.trading.risk.domain.*;
import com.hope.trading.risk.metric.*;
import com.hope.trading.risk.snapshot.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;

import static com.hope.trading.risk.RiskFixture.*;
import static com.hope.trading.risk.domain.RiskTypes.*;
import static org.junit.jupiter.api.Assertions.*;

class MetricAndProjectionTest {
    private final AccountSnapshot account = new AccountSnapshot(
            ACCOUNT_ID, 1, NOW, usd("10000"), usd("9800"), usd("500"),
            usd("10000"), usd("-100"));
    private final ProjectionEngine engine = new ProjectionEngine();

    @Test void computesEveryObservedMetric() {
        var metrics = new ObservedMetricsCalculator().calculate(account);
        assertEquals(new BigDecimal("-200"), metrics.floatingPnl().amount());
        assertEquals(new BigDecimal("-100"), metrics.closedPnl().amount());
        assertEquals(new BigDecimal("9300"), metrics.freeMargin().amount());
    }

    @Test void opensAndIncreasesLongPosition() {
        var opened = engine.project(account, emptyPortfolio(), trade(
                TradeDirection.LONG, "10", "2000", "100", "200"));
        assertPosition(opened, "10", "2000");
        assertMoney(opened.drawdown(), "300");
        var increased = engine.project(account, portfolio("10", "1000", "50", "100"),
                trade(TradeDirection.LONG, "5", "500", "25", "50"));
        assertPosition(increased, "15", "1500");
    }

    @Test void opensAndIncreasesShortPosition() {
        var opened = engine.project(account, emptyPortfolio(), trade(
                TradeDirection.SHORT, "10", "2000", "100", "200"));
        assertPosition(opened, "-10", "2000");
        var increased = engine.project(account, portfolio("-10", "1000", "50", "100"),
                trade(TradeDirection.SHORT, "5", "500", "25", "50"));
        assertPosition(increased, "-15", "1500");
    }

    @Test void reducesAnOppositePositionProportionally() {
        var projected = engine.project(account,
                portfolio("10", "1000", "100", "200"),
                trade(TradeDirection.SHORT, "4", "400", "40", "80"));
        assertPosition(projected, "6", "600");
        assertMoney(projected.portfolioHeat(), "60");
        assertMoney(projected.margin(), "420");
    }

    @Test void closesAnOppositePosition() {
        var projected = engine.project(account,
                portfolio("10", "1000", "100", "200"),
                trade(TradeDirection.SHORT, "10", "1000", "100", "200"));
        assertTrue(projected.portfolioState().positions().isEmpty());
        assertMoney(projected.exposure(), "0");
        assertMoney(projected.margin(), "300");
    }

    @Test void crossesThroughZeroAndOpensOppositeRemainder() {
        var projected = engine.project(account,
                portfolio("10", "1000", "100", "200"),
                trade(TradeDirection.SHORT, "15", "1500", "150", "300"));
        assertPosition(projected, "-5", "500.0000000000000000000000000000000");
        assertMoney(projected.portfolioHeat(), "50.00000000000000000000000000000000");
        assertMoney(projected.margin(), "400.0000000000000000000000000000000");
    }

    @Test void calculatesAllRatiosBeforeRulesRun() {
        var observed = new ObservedMetricsCalculator().calculate(account);
        var trade = trade(TradeDirection.LONG, "10", "2000", "100", "200");
        var projected = engine.project(account, emptyPortfolio(), trade);
        var derived = new DerivedMetricsCalculator().calculate(
                account, observed, projected, trade,
                new Ratio(new BigDecimal("0.02")));
        assertRatio(derived.positionRiskRatio(), "0.01");
        assertRatio(derived.exposureRatio(),
                "0.2040816326530612244897959183673469");
        assertRatio(derived.dailyDrawdownRatio(), "0.03");
        assertMoney(derived.remainingRisk(), "100");
    }

    private PortfolioSnapshot emptyPortfolio() {
        return new PortfolioSnapshot(UUID.randomUUID(), 1, NOW, List.of());
    }
    private PortfolioSnapshot portfolio(String quantity, String exposure,
                                        String loss, String margin) {
        return new PortfolioSnapshot(UUID.randomUUID(), 1, NOW, List.of(
                new PositionSnapshot(UUID.randomUUID(), "EURUSD",
                    new BigDecimal(quantity), usd(exposure), usd(loss), usd(margin))));
    }
    private ProposedTrade trade(TradeDirection direction, String quantity,
                                String notional, String loss, String margin) {
        return new ProposedTrade(UUID.randomUUID(), 1, "EURUSD", direction,
                new BigDecimal(quantity), usd(notional), usd(loss), usd(margin));
    }
    private void assertPosition(ProjectedMetrics metrics, String quantity,
                                String exposure) {
        assertEquals(1, metrics.portfolioState().positions().size());
        var position = metrics.portfolioState().positions().getFirst();
        assertEquals(0, new BigDecimal(quantity).compareTo(position.signedQuantity()));
        assertMoney(position.exposure(), exposure);
    }
    private void assertMoney(Money actual, String expected) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual.amount()));
    }
    private void assertRatio(Ratio actual, String expected) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual.value()));
    }
}
