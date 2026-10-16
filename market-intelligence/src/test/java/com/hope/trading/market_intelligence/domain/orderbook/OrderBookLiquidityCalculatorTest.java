package com.hope.trading.market_intelligence.domain.orderbook;

import com.hope.trading.market_intelligence.domain.OrderBookContext;
import com.hope.trading.market_intelligence.domain.feature.FeatureMissingness;
import com.hope.trading.market_intelligence.domain.feature.FeatureValue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderBookLiquidityCalculatorTest {
    private static final UUID MARKET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SEQUENCE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant CUTOFF = Instant.parse("2026-10-17T12:00:00Z");

    @Test
    void calculatesDeterministicLiquidityMeasurements() {
        OrderBookLiquidityCalculator calculator = new OrderBookLiquidityCalculator();
        OrderBookLiquidityFeatureSet first = calculator.calculate(context(
                List.of(level("100", "2"), level("99", "3")),
                List.of(level("101", "1"), level("102", "4"))));
        OrderBookLiquidityFeatureSet second = calculator.calculate(context(
                List.of(level("100", "2"), level("99", "3")),
                List.of(level("101", "1"), level("102", "4"))));

        assertThat(first.resultFingerprint()).isEqualTo(second.resultFingerprint());
        assertThat(first.values()).extracting(FeatureValue::name).containsExactly(
                "liquidity.best_bid", "liquidity.best_ask", "liquidity.spread_absolute",
                "liquidity.spread_relative", "liquidity.spread_bps", "liquidity.bid_visible_quantity",
                "liquidity.ask_visible_quantity", "liquidity.total_visible_quantity",
                "liquidity.volume_imbalance", "liquidity.microprice");
        assertThat(value(first, "liquidity.spread_absolute")).isEqualByComparingTo("1");
        assertThat(value(first, "liquidity.bid_visible_quantity")).isEqualByComparingTo("5");
        assertThat(value(first, "liquidity.ask_visible_quantity")).isEqualByComparingTo("5");
        assertThat(value(first, "liquidity.volume_imbalance")).isEqualByComparingTo("0");
        assertThat(value(first, "liquidity.microprice")).isEqualByComparingTo("100.6666666666666667");
    }

    @Test
    void makesZeroQuantityDerivedValuesInsufficient() {
        OrderBookLiquidityFeatureSet result = new OrderBookLiquidityCalculator().calculate(context(
                List.of(level("100", "0")), List.of(level("100", "0"))));

        assertThat(status(result, "liquidity.spread_absolute")).isEqualTo(FeatureMissingness.PRESENT);
        assertThat(status(result, "liquidity.volume_imbalance")).isEqualTo(FeatureMissingness.INSUFFICIENT_DATA);
        assertThat(status(result, "liquidity.microprice")).isEqualTo(FeatureMissingness.INSUFFICIENT_DATA);
    }

    @Test
    void marksReplayInputStaleWhenKnownTooFarBeforeCutoff() {
        OrderBookContext stale = new OrderBookContext(MARKET_ID, 25,
                OrderBookContext.ReplayStatus.AVAILABLE, SEQUENCE_ID, CUTOFF,
                CUTOFF.minusSeconds(901), CUTOFF.minusSeconds(901), 0L, 1L,
                "KRAKEN", "PF_XBTUSD", "order-book-normalization-v1", "evidence",
                List.of(level("100", "1")), List.of(level("101", "1")), null);

        OrderBookLiquidityFeatureSet result = new OrderBookLiquidityCalculator().calculate(stale);

        assertThat(result.groups().getFirst().status()).isEqualTo(FeatureMissingness.STALE_INPUT);
        assertThat(result.values()).allSatisfy(value ->
                assertThat(value.status()).isEqualTo(FeatureMissingness.STALE_INPUT));
    }

    @Test
    void canonicalizesEquivalentDecimalScalesInResultFingerprint() {
        OrderBookLiquidityCalculator calculator = new OrderBookLiquidityCalculator();

        OrderBookLiquidityFeatureSet integerScale = calculator.calculate(context(
                List.of(level("100", "2")), List.of(level("101", "1"))));
        OrderBookLiquidityFeatureSet decimalScale = calculator.calculate(context(
                List.of(level("100.0", "2.0")), List.of(level("101.0", "1.0"))));

        assertThat(integerScale.resultFingerprint()).isEqualTo(decimalScale.resultFingerprint());
    }

    @Test
    void rejectsNonNormalizedLevelsInsteadOfReorderingThem() {
        assertThatThrownBy(this::nonNormalizedContext)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not normalized");
    }

    private OrderBookContext nonNormalizedContext() {
        return new OrderBookContext(MARKET_ID, 25,
                OrderBookContext.ReplayStatus.AVAILABLE, SEQUENCE_ID, CUTOFF,
                CUTOFF.minusSeconds(1), CUTOFF, 0L, 1L, "KRAKEN", "PF_XBTUSD",
                "order-book-normalization-v1", "evidence",
                List.of(level("99", "1"), level("100", "1")),
                List.of(level("101", "1")), null);
    }

    @Test
    void rejectsCrossedBooksAndUnavailableReplayExplicitly() {
        OrderBookLiquidityCalculator calculator = new OrderBookLiquidityCalculator();
        OrderBookLiquidityFeatureSet crossed = calculator.calculate(context(
                List.of(level("102", "1")), List.of(level("101", "1"))));
        OrderBookLiquidityFeatureSet unavailable = calculator.calculate(new OrderBookContext(
                MARKET_ID, 25, OrderBookContext.ReplayStatus.UNAVAILABLE, null, CUTOFF,
                null, null, null, null, null, null, null, null, List.of(), List.of(), "no snapshot"));

        assertThat(crossed.values()).isNotEmpty().allSatisfy(value ->
                assertThat(value.status()).isEqualTo(FeatureMissingness.INVALID_INPUT));
        assertThat(unavailable.values()).isNotEmpty().allSatisfy(value ->
                assertThat(value.status()).isEqualTo(FeatureMissingness.UNAVAILABLE));
    }

    private OrderBookContext context(List<OrderBookContext.Level> bids, List<OrderBookContext.Level> asks) {
        return new OrderBookContext(MARKET_ID, 25, OrderBookContext.ReplayStatus.AVAILABLE,
                SEQUENCE_ID, CUTOFF, CUTOFF.minusSeconds(1), CUTOFF,
                0L, 2L, "KRAKEN", "PF_XBTUSD", "order-book-normalization-v1", "evidence",
                bids, asks, null);
    }

    private OrderBookContext.Level level(String price, String quantity) {
        return new OrderBookContext.Level(new BigDecimal(price), new BigDecimal(quantity));
    }

    private BigDecimal value(OrderBookLiquidityFeatureSet result, String name) {
        return result.values().stream().filter(item -> item.name().equals(name))
                .map(item -> (com.hope.trading.market_intelligence.domain.feature.NumericFeatureValue) item)
                .findFirst().orElseThrow().value();
    }

    private FeatureMissingness status(OrderBookLiquidityFeatureSet result, String name) {
        return result.values().stream().filter(item -> item.name().equals(name))
                .findFirst().orElseThrow().status();
    }
}
