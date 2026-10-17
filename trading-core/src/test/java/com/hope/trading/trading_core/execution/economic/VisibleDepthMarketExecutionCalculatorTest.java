package com.hope.trading.trading_core.execution.economic;

import com.hope.trading.trading_core.execution.domain.economic.ReferenceMarketState;
import com.hope.trading.trading_core.execution.domain.economic.SimulatedExecutionStatus;
import com.hope.trading.trading_core.execution.domain.economic.StrategyIntent;
import com.hope.trading.trading_core.execution.domain.economic.VisibleDepthMarketExecutionCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VisibleDepthMarketExecutionCalculatorTest {
    private static final Instant CUTOFF = Instant.parse("2026-10-17T14:00:00Z");
    private static final UUID MARKET_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final VisibleDepthMarketExecutionCalculator calculator = new VisibleDepthMarketExecutionCalculator();

    @Test
    void walksAskLevelsForAFullBuyAndCalculatesVwap() {
        var execution = calculator.simulate(state(
                        List.of(level("99", "5")),
                        List.of(level("100", "0.5"), level("100.1", "0.7"), level("100.2", "1.4"))),
                intent(StrategyIntent.Side.BUY, "2"));

        assertThat(execution.status()).isEqualTo(SimulatedExecutionStatus.FILLED);
        assertThat(execution.filledQuantity()).isEqualByComparingTo("2");
        assertThat(execution.unfilledQuantity()).isZero();
        assertThat(execution.averageExecutionPrice()).isEqualByComparingTo("100.115");
        assertThat(execution.fills()).extracting(value -> value.quantity().toPlainString())
                .containsExactly("0.5", "0.7", "0.8");
    }

    @Test
    void walksBidLevelsForASellInDescendingPriceOrder() {
        var execution = calculator.simulate(state(
                        List.of(level("100", "0.5"), level("99.9", "0.7"), level("99.8", "1.4")),
                        List.of(level("101", "5"))),
                intent(StrategyIntent.Side.SELL, "2"));

        assertThat(execution.status()).isEqualTo(SimulatedExecutionStatus.FILLED);
        assertThat(execution.averageExecutionPrice()).isEqualByComparingTo("99.885");
        assertThat(execution.fills()).extracting(value -> value.price().toPlainString())
                .containsExactly("100", "99.9", "99.8");
    }

    @Test
    void preservesRemainderWhenVisibleDepthIsInsufficient() {
        var execution = calculator.simulate(state(
                        List.of(level("99", "5")), List.of(level("100", "1.7"))),
                intent(StrategyIntent.Side.BUY, "2"));

        assertThat(execution.status()).isEqualTo(SimulatedExecutionStatus.PARTIALLY_FILLED);
        assertThat(execution.filledQuantity()).isEqualByComparingTo("1.7");
        assertThat(execution.unfilledQuantity()).isEqualByComparingTo("0.3");
        assertThat(execution.reason()).isEqualTo("INSUFFICIENT_DEPTH");
    }

    @Test
    void returnsUnfilledWithoutFabricatingPriceWhenOppositeSideIsEmpty() {
        var execution = calculator.simulate(state(
                        List.of(level("99", "5")), List.of()),
                intent(StrategyIntent.Side.BUY, "2"));

        assertThat(execution.status()).isEqualTo(SimulatedExecutionStatus.UNFILLED);
        assertThat(execution.filledQuantity()).isZero();
        assertThat(execution.averageExecutionPrice()).isNull();
        assertThat(execution.fills()).isEmpty();
    }

    @Test
    void changesFingerprintWhenModelVersionChanges() {
        var state = state(List.of(level("99", "5")), List.of(level("100", "5")));
        var intent = intent(StrategyIntent.Side.BUY, "1");

        var first = calculator.simulate(state, intent);
        var second = new VisibleDepthMarketExecutionCalculator("visible-depth-market-execution-v2")
                .simulate(state, intent);

        assertThat(first.executionFingerprint()).isNotEqualTo(second.executionFingerprint());
    }

    @Test
    void rejectsLimitWithoutApproximatingItAsMarket() {
        var limit = new StrategyIntent("BTC/USD", StrategyIntent.Side.BUY,
                StrategyIntent.OrderType.LIMIT, BigDecimal.ONE, CUTOFF);

        var execution = calculator.simulate(state(List.of(level("99", "5")), List.of(level("100", "5"))), limit);

        assertThat(execution.status()).isEqualTo(SimulatedExecutionStatus.UNSUPPORTED_ORDER_TYPE);
        assertThat(execution.filledQuantity()).isZero();
    }

    private ReferenceMarketState state(List<ReferenceMarketState.Level> bids,
                                        List<ReferenceMarketState.Level> asks) {
        return new ReferenceMarketState(MARKET_ID, "BTC/USD", CUTOFF,
                bids.get(0).price(), asks.isEmpty() ? bids.get(0).price() : asks.get(0).price(),
                bids.get(0).price().add(asks.isEmpty() ? bids.get(0).price() : asks.get(0).price())
                        .divide(BigDecimal.valueOf(2)),
                com.hope.trading.trading_core.execution.domain.economic.EconomicProductType.LINEAR_PERPETUAL,
                CUTOFF.minusSeconds(1), "book-fingerprint", bids, asks, UUID.randomUUID(), 0L, 10L,
                "KRAKEN", "PF_XBTUSD", "order-book-normalization-v1");
    }

    private StrategyIntent intent(StrategyIntent.Side side, String quantity) {
        return new StrategyIntent("BTC/USD", side, StrategyIntent.OrderType.MARKET,
                new BigDecimal(quantity), CUTOFF);
    }

    private ReferenceMarketState.Level level(String price, String quantity) {
        return new ReferenceMarketState.Level(new BigDecimal(price), new BigDecimal(quantity));
    }
}
