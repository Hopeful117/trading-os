package com.hope.trading.market_intelligence.strategy.domain;

import com.hope.trading.market_intelligence.strategy.application.BuiltinStrategies;
import com.hope.trading.market_intelligence.strategy.application.StrategyEvaluationContextFactory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Story 0011 domain invariants: only MATCH creates a StrategyMatch; the fact
 * is immutable and faithfully carries its deterministic provenance.
 */
class StrategyMatchTest {

    private static final Instant OBSERVED = Instant.parse("2026-08-21T09:55:00Z");
    private static final Instant EVALUATED = Instant.parse("2026-08-21T10:00:00Z");
    private static final UUID MARKET = UUID.fromString("cccccccc-1111-2222-3333-444444444444");
    private static final UUID ANALYSIS = UUID.fromString("aaaaaaaa-1111-2222-3333-444444444444");
    private static final UUID OBSERVATION = UUID.fromString("bbbbbbbb-1111-2222-3333-444444444444");

    private final BuiltinStrategies builtins = new BuiltinStrategies();
    private final StrategyEvaluationContextFactory factory =
            new StrategyEvaluationContextFactory();

    private StrategyEvaluation matchEvaluation() {
        return StrategyEvaluation.match(
                builtins.legacyOhlcTrend(),
                factory.fromOhlcTrendValues(MARKET, "ETH/USD",
                        StrategyApplicability.Timeframe.M15, EVALUATED,
                        new BigDecimal("1.25"), OBSERVED),
                MatchedDirection.LONG,
                List.of(ConditionResult.of("directional_price_change", true,
                        new BigDecimal("1.25"))),
                BigDecimal.ONE,
                "directional signal",
                java.util.Set.of());
    }

    @Test
    void matchEvaluationCreatesFact() {
        StrategyMatch match = StrategyMatch.fromEvaluation(
                matchEvaluation(), ANALYSIS, OBSERVATION,
                UUID.randomUUID(), Instant.parse("2026-08-21T10:00:01Z"));

        assertThat(match.strategyId().value())
                .isEqualTo(BuiltinStrategies.LEGACY_OHLC_TREND_ID);
        assertThat(match.strategyVersion()).isEqualTo(1);
        assertThat(match.marketId()).isEqualTo(MARKET);
        assertThat(match.analysisExecutionId()).isEqualTo(ANALYSIS);
        assertThat(match.observationId()).isEqualTo(OBSERVATION);
        assertThat(match.direction()).isEqualTo(MatchedDirection.LONG);
        assertThat(match.contextDigest()).isNotBlank();
        assertThat(match.conditionResults()).hasSize(1);
        assertThat(match.conditionResults().get(0).passed()).isTrue();
        // matchedAt == evaluatedAt
        assertThat(match.matchedAt()).isEqualTo(EVALUATED);
        assertThat(match.createdAt()).isAfter(match.matchedAt());
    }

    @Test
    void noMatchNeverCreatesFact() {
        StrategyEvaluation noMatch = StrategyEvaluation.noMatch(
                builtins.legacyOhlcTrend(),
                factory.fromOhlcTrendValues(MARKET, "ETH/USD",
                        StrategyApplicability.Timeframe.M15, EVALUATED,
                        BigDecimal.ZERO, OBSERVED),
                List.of(ConditionResult.of("directional_price_change", false, (java.math.BigDecimal) null)),
                "no signal",
                java.util.Set.of());
        assertThatThrownBy(() -> StrategyMatch.fromEvaluation(
                noMatch, ANALYSIS, OBSERVATION,
                UUID.randomUUID(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MATCH");
    }

    @Test
    void notEvaluableNeverCreatesFact() {
        StrategyEvaluation evaluation = StrategyEvaluation.notEvaluable(
                builtins.legacyOhlcTrend(),
                factory.fromOhlcTrendValues(MARKET, "ETH/USD",
                        StrategyApplicability.Timeframe.M15, EVALUATED,
                        BigDecimal.ONE, OBSERVED),
                "missing input");
        assertThatThrownBy(() -> StrategyMatch.fromEvaluation(
                evaluation, ANALYSIS, OBSERVATION, UUID.randomUUID(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failedNeverCreatesFact() {
        StrategyEvaluation evaluation = StrategyEvaluation.failed(
                builtins.legacyOhlcTrend(),
                factory.fromOhlcTrendValues(MARKET, "ETH/USD",
                        StrategyApplicability.Timeframe.M15, EVALUATED,
                        BigDecimal.ONE, OBSERVED),
                "boom");
        assertThatThrownBy(() -> StrategyMatch.fromEvaluation(
                evaluation, ANALYSIS, OBSERVATION, UUID.randomUUID(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void conditionResultsAreDefensivelyCopied() {
        List<ConditionResult> mutable = new ArrayList<>();
        mutable.add(ConditionResult.of("directional_price_change", true, BigDecimal.TEN));
        StrategyMatch match = StrategyMatch.rehydrate(
                UUID.randomUUID(), new StrategyId(BuiltinStrategies.LEGACY_OHLC_TREND_ID),
                1, MARKET, ANALYSIS, OBSERVATION, MatchedDirection.LONG, "digest",
                mutable, EVALUATED, EVALUATED);

        mutable.clear();
        assertThat(match.conditionResults()).hasSize(1);
        assertThatThrownBy(() -> match.conditionResults()
                .add(ConditionResult.of("x", false, null)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void directionIsRequired() {
        assertThatThrownBy(() -> StrategyMatch.rehydrate(
                UUID.randomUUID(), new StrategyId(BuiltinStrategies.LEGACY_OHLC_TREND_ID),
                1, MARKET, ANALYSIS, OBSERVATION, null, "digest",
                List.of(), EVALUATED, EVALUATED))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("direction");
    }

    @Test
    void rehydrationIsFaithfulAndIdentityMatchesBusinessKey() {
        UUID matchId = UUID.randomUUID();
        StrategyMatch match = StrategyMatch.rehydrate(
                matchId, new StrategyId(BuiltinStrategies.LEGACY_OHLC_TREND_ID), 7,
                MARKET, ANALYSIS, OBSERVATION, MatchedDirection.SHORT, "digest-x",
                List.of(ConditionResult.of("c", false, (java.math.BigDecimal) null)), EVALUATED, EVALUATED);

        assertThat(match.matchId()).isEqualTo(matchId);
        assertThat(match.identity()).isEqualTo(new StrategyMatchIdentity(
                BuiltinStrategies.LEGACY_OHLC_TREND_ID, 7, MARKET, ANALYSIS, "digest-x"));
        // equality is technical identity (matchId)
        assertThat(match).isEqualTo(StrategyMatch.rehydrate(
                matchId, new StrategyId(BuiltinStrategies.LEGACY_OHLC_TREND_ID), 7,
                MARKET, ANALYSIS, OBSERVATION, MatchedDirection.SHORT, "digest-x",
                List.of(ConditionResult.of("c", false, (java.math.BigDecimal) null)), EVALUATED, EVALUATED));
    }
}
