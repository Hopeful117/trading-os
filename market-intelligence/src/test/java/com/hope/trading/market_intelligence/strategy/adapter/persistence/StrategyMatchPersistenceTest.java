package com.hope.trading.market_intelligence.strategy.adapter.persistence;

import com.hope.trading.market_intelligence.strategy.application.BuiltinStrategies;
import com.hope.trading.market_intelligence.strategy.application.StrategyEvaluationContextFactory;
import com.hope.trading.market_intelligence.strategy.application.StrategyMatchPersister;
import com.hope.trading.market_intelligence.strategy.domain.ConditionResult;
import com.hope.trading.market_intelligence.strategy.domain.MatchedDirection;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Story 0012 persistence semantics against real Flyway-managed schema
 * (H2 PostgreSQL mode): round-trip fidelity, business-key idempotency,
 * authoritative uniqueness, independence from strategy_definitions rows.
 */
@SpringBootTest
@ActiveProfiles("test")
class StrategyMatchPersistenceTest {

    private static final Instant MATCHED = Instant.parse("2026-08-21T10:00:00Z");
    private static final UUID STRATEGY = UUID.fromString("0a10c7e2-9d1e-4f5a-b6c8-123456789001");
    private static final UUID MARKET = UUID.fromString("cccccccc-1111-2222-3333-444444444444");
    private static final UUID ANALYSIS = UUID.randomUUID();
    private static final UUID OBSERVATION = UUID.fromString("bbbbbbbb-1111-2222-3333-444444444444");

    @Autowired com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository repository;
    @Autowired StrategyMatchPersister persister;

    private StrategyEvaluation evaluation(long evaluatedAtOffsetSeconds) {
        return StrategyEvaluation.match(
                new BuiltinStrategies().legacyOhlcTrend(),
                new StrategyEvaluationContextFactory().fromOhlcTrendValues(
                        MARKET, "ETH/USD",
                        com.hope.trading.market_intelligence.strategy.domain
                                .StrategyApplicability.Timeframe.M15,
                        MATCHED.plusSeconds(evaluatedAtOffsetSeconds),
                        new BigDecimal("469.88"), MATCHED.minusSeconds(60)),
                MatchedDirection.LONG,
                List.of(new ConditionResult("directional_price_change", true,
                        new BigDecimal("469.88").toPlainString())),
                BigDecimal.ONE, null, java.util.Set.of());
    }

    @Test
    void persistsAndReloadsFaithfully() {
        var result = persister.persist(evaluation(0), ANALYSIS, OBSERVATION).orElseThrow();
        assertThat(result.created()).isTrue();

        Optional<com.hope.trading.market_intelligence.strategy.domain.StrategyMatch> reloaded =
                repository.findById(result.match().matchId());
        assertThat(reloaded).isPresent();
        var match = reloaded.get();
        assertThat(match.strategyId().value()).isEqualTo(STRATEGY);
        assertThat(match.strategyVersion()).isEqualTo(1);
        assertThat(match.marketId()).isEqualTo(MARKET);
        assertThat(match.analysisExecutionId()).isEqualTo(ANALYSIS);
        assertThat(match.observationId()).isEqualTo(OBSERVATION);
        assertThat(match.direction()).isEqualTo(MatchedDirection.LONG);
        assertThat(match.matchedAt()).isEqualTo(MATCHED);
        assertThat(match.createdAt()).isAfterOrEqualTo(match.matchedAt());
        assertThat(match.conditionResults()).containsExactly(
                new ConditionResult("directional_price_change", true,
                        new BigDecimal("469.88").toPlainString()));
        // context digest round-trips exactly as produced by the evaluator
        assertThat(match.contextDigest()).isEqualTo(result.match().contextDigest());
    }

    @Test
    void strategyDefinitionRowIsNotRequired() {
        var result = persister.persist(evaluation(1000), ANALYSIS, OBSERVATION).orElseThrow();
        assertThat(repository.findById(result.match().matchId())).isPresent();
    }

    @Test
    void databaseUniquenessIsAuthoritative() {
        persister.persist(evaluation(2000), ANALYSIS, OBSERVATION);
        String digest = repository.findByAnalysisExecutionId(ANALYSIS).stream()
                .filter(m -> m.matchedAt().equals(MATCHED.plusSeconds(2000)))
                .findFirst().orElseThrow().contextDigest();

        // raw second insert of the same logical identity must hit the constraint
        assertThatThrownBy(() -> repository.save(
                com.hope.trading.market_intelligence.strategy.domain.StrategyMatch.rehydrate(
                        UUID.randomUUID(), new com.hope.trading.market_intelligence.strategy.domain.StrategyId(STRATEGY),
                        1, MARKET, ANALYSIS, OBSERVATION, MatchedDirection.LONG,
                        digest,
                        List.of(new ConditionResult("c", true, "1")),
                        MATCHED.plusSeconds(2000), MATCHED)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameLogicalEvaluationPersistsOnce() {
        var evaluation = evaluation(3000);
        var first = persister.persist(evaluation, ANALYSIS, OBSERVATION).orElseThrow();
        var second = persister.persist(evaluation, ANALYSIS, OBSERVATION).orElseThrow();
        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.match().matchId()).isEqualTo(first.match().matchId());

        UUID otherAnalysis = UUID.randomUUID();
        persister.persist(evaluation, otherAnalysis, OBSERVATION);
        assertThat(repository.findByAnalysisExecutionId(otherAnalysis)).hasSize(1);
        assertThat(repository.findByAnalysisExecutionId(otherAnalysis).get(0))
                .usingRecursiveComparison()
                .usingOverriddenEquals()
                .isNotEqualTo(first.match());
    }

    @Test
    void distinctContextYieldsDistinctMatches() {
        persister.persist(evaluation(4000), ANALYSIS, OBSERVATION);
        var second = persister.persist(evaluation(4001), ANALYSIS, OBSERVATION).orElseThrow();
        assertThat(second.created()).isTrue(); // different digest -> distinct row
    }
}
