package com.hope.trading.market_intelligence.strategy.application;

import com.hope.trading.market_intelligence.strategy.domain.MatchedDirection;
import com.hope.trading.market_intelligence.strategy.domain.StrategyApplicability;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluation;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluationContext;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluationStatus;
import org.junit.jupiter.api.Test;
import com.hope.trading.market_intelligence.strategy.domain.StrategyDefinition;


import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyOhlcTrendEvaluatorTest {

    private static final Instant NOW = Instant.parse("2026-08-21T10:00:00Z");
    private static final Instant OBSERVED = NOW.minus(Duration.ofMinutes(5));
    private static final UUID MARKET = UUID.fromString("aaaaaaaa-1111-2222-3333-444444444444");

    private final BuiltinStrategies builtins = new BuiltinStrategies();
    private final StrategyEvaluatorRegistry registry =
            new StrategyEvaluatorRegistry(List.of(new LegacyOhlcTrendEvaluator()));

    private StrategyEvaluationContext context(String priceChange) {
        return context(priceChange, OBSERVED);
    }

    private StrategyEvaluationContext context(String priceChange, Instant observedAt) {
        return new StrategyEvaluationContextFactory().fromOhlcTrendValues(
                MARKET, "ETH/USD", StrategyApplicability.Timeframe.M15, NOW,
                priceChange == null ? null : new BigDecimal(priceChange), observedAt);
    }

    @Test
    void positiveTrendMatchesLong() {
        StrategyEvaluation evaluation = registry.evaluate(
                builtins.legacyOhlcTrend(), context("12.5"));
        assertThat(evaluation.status()).isEqualTo(StrategyEvaluationStatus.MATCH);
        assertThat(evaluation.isMatch()).isTrue();
        assertThat(evaluation.direction()).contains(MatchedDirection.LONG);
        assertThat(evaluation.confidence()).hasValue(BigDecimal.ONE);
    }

    @Test
    void negativeTrendMatchesShort() {
        StrategyEvaluation evaluation = registry.evaluate(
                builtins.legacyOhlcTrend(), context("-3.25"));
        assertThat(evaluation.status()).isEqualTo(StrategyEvaluationStatus.MATCH);
        assertThat(evaluation.direction()).contains(MatchedDirection.SHORT);
    }

    @Test
    void zeroTrendIsNoMatchNotFailure() {
        StrategyEvaluation evaluation = registry.evaluate(
                builtins.legacyOhlcTrend(), context("0"));
        assertThat(evaluation.status()).isEqualTo(StrategyEvaluationStatus.NO_MATCH);
        assertThat(evaluation.direction()).isEmpty();
        assertThat(evaluation.conditionResults()).hasSize(1);
        assertThat(evaluation.conditionResults().get(0).passed()).isFalse();
    }

    @Test
    void missingInputIsNotEvaluable() {
        StrategyEvaluation evaluation = registry.evaluate(builtins.legacyOhlcTrend(),
                StrategyEvaluationContext.builder()
                        .marketId(MARKET).instrument("ETH/USD")
                        .timeframe(StrategyApplicability.Timeframe.M15)
                        .evaluatedAt(NOW)
                        .build());
        assertThat(evaluation.status()).isEqualTo(StrategyEvaluationStatus.NOT_EVALUABLE);
    }

    @Test
    void repeatedEvaluationIsDeterministic() {
        StrategyDefinition definition = builtins.legacyOhlcTrend();
        StrategyEvaluationContext context = context("7");
        StrategyEvaluation first = registry.evaluate(definition, context);
        StrategyEvaluation second = registry.evaluate(definition, context);
        assertThat(first.status()).isEqualTo(second.status());
        assertThat(first.direction()).isEqualTo(second.direction());
        assertThat(first.contextDigest()).isEqualTo(second.contextDigest());
        assertThat(first.evaluatedAt()).isEqualTo(NOW);
    }

    @Test
    void suppliedTimestampIsAuthoritativeForValidity() {
        Instant observedEarlier = NOW.minus(Duration.ofHours(2));
        StrategyEvaluation evaluation = registry.evaluate(
                builtins.legacyOhlcTrend(), context("5", observedEarlier));
        // validity derives from observedAt + 30m, both supplied, never wall-clock
        String explanation = evaluation.explanation();
        assertThat(explanation).contains(observedEarlier.plus(Duration.ofMinutes(30)).toString());
    }

    @Test
    void evaluationCarriesExactStrategyAttribution() {
        StrategyEvaluation evaluation = registry.evaluate(
                builtins.legacyOhlcTrend(), context("1"));
        assertThat(evaluation.strategyId().value())
                .isEqualTo(BuiltinStrategies.LEGACY_OHLC_TREND_ID);
        assertThat(evaluation.strategyVersion())
                .isEqualTo(BuiltinStrategies.LEGACY_OHLC_TREND_VERSION);
        assertThat(evaluation.marketId()).isEqualTo(MARKET);
    }

    @Test
    void bootstrapDefinitionRemainsUnvalidated() {
        var definition = builtins.legacyOhlcTrend();
        assertThat(definition.validationStatus()
                .name()).isEqualTo("UNVALIDATED");
        assertThat(definition.lifecycle())
                .isEqualTo(com.hope.trading.market_intelligence.strategy.domain
                        .StrategyLifecycle.DRAFT);
    }
}
