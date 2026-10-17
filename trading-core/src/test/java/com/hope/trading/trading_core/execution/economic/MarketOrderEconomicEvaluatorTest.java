package com.hope.trading.trading_core.execution.economic;

import com.hope.trading.trading_core.execution.domain.economic.EconomicResultStatus;
import com.hope.trading.trading_core.execution.domain.economic.CostEvidence;
import com.hope.trading.trading_core.execution.domain.economic.MarketOrderEconomicEvaluator;
import com.hope.trading.trading_core.execution.domain.economic.ReferenceMarketState;
import com.hope.trading.trading_core.execution.domain.economic.StrategyIntent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MarketOrderEconomicEvaluatorTest {
    private static final Instant CUTOFF = Instant.parse("2026-10-17T10:00:00Z");
    private final MarketOrderEconomicEvaluator evaluator = new MarketOrderEconomicEvaluator();

    @Test
    void calculatesExecutionImpactFeeAndFundingOnce() {
        var result = evaluator.evaluate(state(),
                new StrategyIntent("BTC/USD", StrategyIntent.Side.BUY, StrategyIntent.OrderType.MARKET,
                        new BigDecimal("2"), CUTOFF.minusSeconds(1)),
                new BigDecimal("0.001"), new BigDecimal("-0.010"), evidence("fee"), evidence("funding"),
                "market-order-v1");

        assertThat(result.status()).isEqualTo(EconomicResultStatus.AVAILABLE);
        assertThat(result.execution().fillPrice()).isEqualByComparingTo("101");
        assertThat(result.costs().executionImpact()).isEqualByComparingTo("2");
        assertThat(result.costs().fee()).isEqualByComparingTo("0.202");
        assertThat(result.costs().funding()).isEqualByComparingTo("-0.010");
        assertThat(result.costs().total()).isEqualByComparingTo("2.192");
        assertThat(result.calculationFingerprint()).hasSize(64);
    }

    @Test
    void doesNotDoubleCountSpreadAsSeparateSlippage() {
        var result = evaluator.evaluate(state(),
                new StrategyIntent("BTC/USD", StrategyIntent.Side.SELL, StrategyIntent.OrderType.MARKET,
                        BigDecimal.ONE, CUTOFF),
                BigDecimal.ZERO, BigDecimal.ZERO, evidence("fee"), evidence("funding"), "market-order-v1");

        assertThat(result.execution().fillPrice()).isEqualByComparingTo("99");
        assertThat(result.costs().executionImpact()).isEqualByComparingTo("1");
        assertThat(result.costs().total()).isEqualByComparingTo("1");
    }

    @Test
    void rejectsUnsupportedOrderTypeAndStaleDecisionExplicitly() {
        var unsupported = evaluator.evaluate(state(),
                new StrategyIntent("BTC/USD", StrategyIntent.Side.BUY, StrategyIntent.OrderType.LIMIT,
                        BigDecimal.ONE, CUTOFF.minusSeconds(1)), BigDecimal.ZERO, BigDecimal.ZERO,
                evidence("fee"), evidence("funding"), "v1");
        var stale = evaluator.evaluate(state(),
                new StrategyIntent("BTC/USD", StrategyIntent.Side.BUY, StrategyIntent.OrderType.MARKET,
                        BigDecimal.ONE, CUTOFF.plusSeconds(1)), BigDecimal.ZERO, BigDecimal.ZERO,
                evidence("fee"), evidence("funding"), "v1");

        assertThat(unsupported.status()).isEqualTo(EconomicResultStatus.UNSUPPORTED);
        assertThat(stale.status()).isEqualTo(EconomicResultStatus.STALE);
    }

    @Test
    void rejectsMarketStateLearnedAfterDecisionTime() {
        var state = new ReferenceMarketState(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "BTC/USD", CUTOFF, new BigDecimal("99"), new BigDecimal("101"), new BigDecimal("100"),
                CUTOFF.minusMillis(500), "order-book-fingerprint");
        var result = evaluator.evaluate(state,
                new StrategyIntent("BTC/USD", StrategyIntent.Side.BUY, StrategyIntent.OrderType.MARKET,
                        BigDecimal.ONE, CUTOFF.minusSeconds(1)),
                BigDecimal.ZERO, BigDecimal.ZERO, evidence("fee"), evidence("funding"), "v1");

        assertThat(result.status()).isEqualTo(EconomicResultStatus.STALE);
    }

    @Test
    void missingFundingIsNotConvertedToZero() {
        var result = evaluator.evaluate(state(),
                new StrategyIntent("BTC/USD", StrategyIntent.Side.BUY, StrategyIntent.OrderType.MARKET,
                        BigDecimal.ONE, CUTOFF), BigDecimal.ZERO, null, evidence("fee"), evidence("funding"), "v1");

        assertThat(result.status()).isEqualTo(EconomicResultStatus.INSUFFICIENT_DATA);
        assertThat(result.costs()).isNull();
    }

    @Test
    void missingCostProvenanceIsDegradedAndNotAvailable() {
        var result = evaluator.evaluate(state(),
                new StrategyIntent("BTC/USD", StrategyIntent.Side.BUY, StrategyIntent.OrderType.MARKET,
                        BigDecimal.ONE, CUTOFF), BigDecimal.ZERO, BigDecimal.ZERO, null, evidence("funding"), "v1");

        assertThat(result.status()).isEqualTo(EconomicResultStatus.DEGRADED);
        assertThat(result.costs()).isNull();
    }

    private ReferenceMarketState state() {
        return new ReferenceMarketState(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "BTC/USD", CUTOFF, new BigDecimal("99"), new BigDecimal("101"), new BigDecimal("100"),
                CUTOFF.minusSeconds(1), "order-book-fingerprint");
    }

    private CostEvidence evidence(String name) {
        return new CostEvidence(name, name + "-source", CUTOFF.minusSeconds(60),
                CUTOFF.plusSeconds(60), CUTOFF.minusSeconds(30));
    }
}
