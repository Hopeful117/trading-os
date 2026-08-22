package com.hope.trading.market_intelligence.strategy.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategyEvaluationTest {

    private static final Instant NOW = Instant.parse("2026-08-21T10:00:00Z");
    private static final UUID MARKET = UUID.fromString("bbbbbbbb-1111-2222-3333-444444444444");

    private static StrategyDefinition definition() {
        return StrategyDefinition.create(
                new StrategyId(UUID.fromString("0a10c7e2-9d1e-4f5a-b6c8-123456789001")),
                1, "Legacy OHLC Trend", null, "OHLC_TREND", StrategyDirection.DYNAMIC,
                new StrategyApplicability(Set.of("CRYPTO"),
                        Set.of(StrategyApplicability.Timeframe.M15), Set.of()),
                Set.of(new RequiredSemanticInput(SemanticInputType.OBSERVATION, "X")),
                StrategyParameters.empty(), null, NOW);
    }

    private static StrategyEvaluationContext context() {
        return StrategyEvaluationContext.builder()
                .marketId(MARKET).instrument("ETH/USD")
                .timeframe(StrategyApplicability.Timeframe.M15).evaluatedAt(NOW)
                .input(new RequiredSemanticInput(SemanticInputType.OBSERVATION, "X"),
                        StrategyEvaluationContext.SemanticValue.decimal(BigDecimal.ONE))
                .build();
    }

    @Test
    void matchRequiresDirection() {
        assertThatThrownBy(() -> StrategyEvaluation.build(
                definition(), context(), StrategyEvaluationStatus.MATCH, null,
                List.of(), BigDecimal.ONE, "x", Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        StrategyEvaluation evaluation = StrategyEvaluation.match(definition(), context(),
                MatchedDirection.LONG, List.of(), BigDecimal.ONE, "ok", Set.of());
        assertThat(evaluation.isMatch()).isTrue();
        assertThat(evaluation.direction()).contains(MatchedDirection.LONG);
    }

    @Test
    void nonMatchStatusesForbidDirection() {
        for (StrategyEvaluationStatus status : new StrategyEvaluationStatus[] {
                StrategyEvaluationStatus.NO_MATCH,
                StrategyEvaluationStatus.NOT_EVALUABLE,
                StrategyEvaluationStatus.FAILED}) {
            assertThatThrownBy(() -> StrategyEvaluation.build(
                    definition(), context(), status, MatchedDirection.LONG,
                    List.of(), null, "x", Set.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        StrategyEvaluation noMatch = StrategyEvaluation.noMatch(definition(), context(),
                List.of(), "no signal", Set.of());
        assertThat(noMatch.status()).isEqualTo(StrategyEvaluationStatus.NO_MATCH);
        assertThat(noMatch.direction()).isEmpty();
        assertThat(noMatch.isMatch()).isFalse();
    }

    @Test
    void digestIsStableAcrossEquivalentContextsAndOrderIndependent() {
        RequiredSemanticInput a = new RequiredSemanticInput(SemanticInputType.OBSERVATION, "A");
        RequiredSemanticInput b = new RequiredSemanticInput(SemanticInputType.OBSERVATION, "B");
        var first = StrategyEvaluationContext.builder()
                .marketId(MARKET).instrument("ETH/USD")
                .timeframe(StrategyApplicability.Timeframe.M15).evaluatedAt(NOW)
                .input(a, StrategyEvaluationContext.SemanticValue.decimal(new BigDecimal("1.50")))
                .input(b, StrategyEvaluationContext.SemanticValue.instant(NOW))
                .build();
        var second = StrategyEvaluationContext.builder()
                .marketId(MARKET).instrument("ETH/USD")
                .timeframe(StrategyApplicability.Timeframe.M15).evaluatedAt(NOW)
                .input(b, StrategyEvaluationContext.SemanticValue.instant(NOW))
                .input(a, StrategyEvaluationContext.SemanticValue.decimal(new BigDecimal("1.5")))
                .build();
        assertThat(first.digest()).isEqualTo(second.digest());

        var different = StrategyEvaluationContext.builder()
                .marketId(MARKET).instrument("ETH/USD")
                .timeframe(StrategyApplicability.Timeframe.M15).evaluatedAt(NOW.plusSeconds(1))
                .input(a, StrategyEvaluationContext.SemanticValue.decimal(new BigDecimal("1.50")))
                .input(b, StrategyEvaluationContext.SemanticValue.instant(NOW))
                .build();
        assertThat(different.digest()).isNotEqualTo(first.digest());
    }

    @Test
    void semanticValuesAreTyped() {
        var decimal = StrategyEvaluationContext.SemanticValue.decimal(BigDecimal.TEN);
        var instant = StrategyEvaluationContext.SemanticValue.instant(NOW);
        assertThat(decimal.decimalValue()).isEqualByComparingTo("10");
        assertThatThrownBy(decimal::instantValue).isInstanceOf(IllegalStateException.class);
        assertThat(instant.instantValue()).isEqualTo(NOW);
        assertThat(StrategyEvaluationContext.SemanticValue.duration(Duration.ofMinutes(5))
                .durationValue()).isEqualTo(Duration.ofMinutes(5));
        assertThatThrownBy(() ->
                StrategyEvaluationContext.SemanticValue.decimal(null))
                .isInstanceOf(NullPointerException.class);
    }
}
