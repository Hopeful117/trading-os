package com.hope.trading.market_intelligence.strategy.application;

import com.hope.trading.market_intelligence.strategy.domain.ConditionResult;
import com.hope.trading.market_intelligence.strategy.domain.MatchedDirection;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluation;
import com.hope.trading.market_intelligence.strategy.domain.StrategyId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Story 0012 required-truth transaction semantics: the StrategyMatch persists
 * inline in the caller's transaction (REQUIRED, never REQUIRES_NEW, never
 * afterCommit). A rollback of the surrounding T1 removes the match too; a
 * committed retry is idempotent.
 */
@SpringBootTest
@ActiveProfiles("test")
class StrategyMatchRequiredTruthTest {

    private static final Instant MATCHED = Instant.parse("2026-08-21T12:00:00Z");
    private static final UUID STRATEGY =
            UUID.fromString("0a10c7e2-9d1e-4f5a-b6c8-123456789001");

    @Autowired StrategyMatchPersister persister;
    @Autowired com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository repository;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;

    private StrategyEvaluation matchEvaluation(UUID market) {
        return StrategyEvaluation.match(
                new BuiltinStrategies().legacyOhlcTrend(),
                new StrategyEvaluationContextFactory().fromOhlcTrendValues(
                        market, "ETH/USD",
                        com.hope.trading.market_intelligence.strategy.domain
                                .StrategyApplicability.Timeframe.M15,
                        MATCHED, new BigDecimal("5"), MATCHED.minusSeconds(60)),
                MatchedDirection.LONG,
                List.of(ConditionResult.of("directional_price_change", true,
                        new BigDecimal("5"))),
                BigDecimal.ONE, null, java.util.Set.of());
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactions);
    }

    private int countRows(UUID analysis) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select count(*) as c from strategy_matches where analysis_execution_id = ?",
                analysis);
        return ((Number) rows.get(0).get("c")).intValue();
    }

    @Test
    void matchParticipatesInCallerTransactionAndRollsBackWithIt() {
        UUID analysis = UUID.randomUUID();
        tx().executeWithoutResult(status -> {
            var result = persister.persist(
                    matchEvaluation(UUID.randomUUID()), analysis, UUID.randomUUID()).orElseThrow();
            // visible inside the same transaction through the shared persistence context
            assertThat(repository.findById(result.match().matchId())).isPresent();
            status.setRollbackOnly();
        });
        // ...and gone after rollback: no orphan required-truth match.
        assertThat(countRows(analysis)).isZero();
    }

    @Test
    void committedMatchIsIdempotentOnRetry() {
        UUID analysis = UUID.randomUUID();
        var evaluation = matchEvaluation(UUID.randomUUID());
        tx().executeWithoutResult(status ->
                persister.persist(evaluation, analysis, UUID.randomUUID()));
        tx().executeWithoutResult(status -> {
            var retry = persister.persist(evaluation, analysis, UUID.randomUUID()).orElseThrow();
            assertThat(retry.created()).isFalse(); // ALREADY_EXISTS
        });
        assertThat(countRows(analysis)).isEqualTo(1);
    }

    @Test
    void concurrentDuplicatePersistsOneLogicalRow() throws Exception {
        UUID analysis = UUID.randomUUID();
        var evaluation = matchEvaluation(UUID.randomUUID());
        // The database unique constraint is the final protection: whichever
        // transaction loses the race fails at commit and must retry; exactly
        // one logical row survives.
        java.util.concurrent.atomic.AtomicReference<Throwable> loser =
                new java.util.concurrent.atomic.AtomicReference<>();
        Thread other = new Thread(() -> {
            TransactionTemplate template = new TransactionTemplate(transactions);
            try {
                template.executeWithoutResult(status ->
                        persister.persist(evaluation, analysis, UUID.randomUUID()));
            } catch (RuntimeException exception) {
                loser.set(exception);
            }
        });
        other.start();
        RuntimeException mine = null;
        try {
            tx().executeWithoutResult(status ->
                    persister.persist(evaluation, analysis, UUID.randomUUID()));
        } catch (RuntimeException exception) {
            mine = exception;
        }
        other.join();

        assertThat(mine == null || loser.get() == null)
                .as("exactly one racing transaction wins").isTrue();
        assertThat(countRows(analysis)).isEqualTo(1);
        // the losing transaction can safely retry: ALREADY_EXISTS
        tx().executeWithoutResult(status -> {
            var retry = persister.persist(evaluation, analysis, UUID.randomUUID()).orElseThrow();
            assertThat(retry.created()).isFalse();
        });
        assertThat(countRows(analysis)).isEqualTo(1);
    }

    @Test
    void nonMatchNeverPersistsAnything() {
        UUID analysis = UUID.randomUUID();
        StrategyEvaluation noMatch = StrategyEvaluation.noMatch(
                new BuiltinStrategies().legacyOhlcTrend(),
                new StrategyEvaluationContextFactory().fromOhlcTrendValues(
                        UUID.randomUUID(), "ETH/USD",
                        com.hope.trading.market_intelligence.strategy.domain
                                .StrategyApplicability.Timeframe.M15,
                        MATCHED, BigDecimal.ZERO, MATCHED),
                List.of(), "no signal", java.util.Set.of());
        tx().executeWithoutResult(status ->
                assertThat(persister.persist(noMatch, analysis, UUID.randomUUID())).isEmpty());
        assertThat(countRows(analysis)).isZero();
    }
}
