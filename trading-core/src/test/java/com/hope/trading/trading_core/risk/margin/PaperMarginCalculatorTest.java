package com.hope.trading.trading_core.risk.margin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hope.trading.trading_core.market_data.valuation.MarketValuationPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaperMarginCalculatorTest {
    private static final Instant NOW = Instant.parse("2026-10-31T12:00:00Z");

    @Test
    void validatesRateAndCalculatesNettingTransitions() {
        MarginPolicy policy = policy("0.01", 2);
        PaperMarginCalculator calculator = new PaperMarginCalculator();
        assertThat(calculator.calculate(policy, request(BigDecimal.ZERO, new BigDecimal("1000"), "USD", List.of())).amount())
                .isEqualByComparingTo("11.00");
        assertThat(calculator.calculate(policy, request(new BigDecimal("1000"), new BigDecimal("-400"), "USD", List.of())).amount())
                .isEqualByComparingTo("0.00");
        assertThat(calculator.calculate(policy, request(new BigDecimal("1000"), new BigDecimal("-2500"), "USD", List.of())).amount())
                .isEqualByComparingTo("5.50");
    }

    @Test
    void convertsBeforeCeilingRounding() {
        MarginPolicy policy = policy("0.00000001", 2);
        var leg = new MarketValuationPort.ConversionLeg("EUR", "USD", new BigDecimal("1.23456"),
                "FX", "market-data", NOW.minusSeconds(1), NOW.minusSeconds(1));
        var result = new PaperMarginCalculator().calculate(policy,
                request(BigDecimal.ZERO, new BigDecimal("100000000"), "USD", List.of(leg), "EUR"));
        assertThat(result.amount()).isEqualByComparingTo("1.36");
    }

    @Test
    void rejectsExcessiveRatePrecisionAndReverseFx() {
        assertThatThrownBy(() -> policy("0.123456789", 2)).isInstanceOf(IllegalArgumentException.class);
        var leg = new MarketValuationPort.ConversionLeg("USD", "EUR", BigDecimal.ONE, "FX", "source",
                NOW, NOW);
        assertThat(new PaperMarginCalculator().calculate(policy("0.01", 2),
                request(BigDecimal.ZERO, BigDecimal.ONE, "USD", List.of(leg), "EUR")).available()).isFalse();
    }

    @Test
    void rejectsStaleFxPath() {
        var stale = new MarketValuationPort.ConversionLeg("EUR", "USD", BigDecimal.ONE,
                "FX", "source", NOW.minusSeconds(360), NOW.minusSeconds(360));

        assertThat(new PaperMarginCalculator().calculate(policy("0.01", 2),
                request(BigDecimal.ZERO, BigDecimal.ONE, "USD", List.of(stale), "EUR")).available()).isFalse();
    }

    @Test
    void rejectsComposedRateMismatch() {
        var leg = new MarketValuationPort.ConversionLeg("EUR", "USD", new BigDecimal("1.2"),
                "FX", "source", NOW, NOW);
        var request = new PaperMarginCalculator.Request("EUR/USD", BigDecimal.ZERO, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, "EUR", "USD", List.of(leg), NOW,
                java.time.Duration.ofMinutes(5), new BigDecimal("1.3"));
        assertThat(new PaperMarginCalculator().calculate(policy("0.01", 2), request).reason())
                .isEqualTo("FX_RATE_MISMATCH");
    }

    private static MarginPolicy policy(String rate, int scale) {
        return new MarginPolicy(UUID.randomUUID(), "v1", "EUR/USD", new BigDecimal(rate),
                "USER_CONFIGURED", "test", NOW.minusSeconds(1), NOW.minusSeconds(1), scale,
                false, false, false, false, false);
    }

    private static PaperMarginCalculator.Request request(BigDecimal existing, BigDecimal proposed,
                                                         String currency, List<MarketValuationPort.ConversionLeg> fx) {
        return request(existing, proposed, currency, fx, currency);
    }

    private static PaperMarginCalculator.Request request(BigDecimal existing, BigDecimal proposed,
                                                         String reporting, List<MarketValuationPort.ConversionLeg> fx,
                                                         String quote) {
        return new PaperMarginCalculator.Request("EUR/USD", existing, proposed, new BigDecimal("1.10"),
                BigDecimal.ONE, quote, reporting, fx, NOW, java.time.Duration.ofMinutes(5),
                quote.equalsIgnoreCase(reporting) ? BigDecimal.ONE : fx.stream()
                        .map(MarketValuationPort.ConversionLeg::rate).reduce(BigDecimal.ONE, BigDecimal::multiply));
    }
}
