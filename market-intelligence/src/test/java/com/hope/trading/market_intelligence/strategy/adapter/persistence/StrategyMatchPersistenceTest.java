package com.hope.trading.market_intelligence.strategy.adapter.persistence;

import com.hope.trading.market_intelligence.strategy.application.StrategyMatchPersistResult;
import com.hope.trading.market_intelligence.strategy.application.StrategyMatchPersister;
import com.hope.trading.market_intelligence.strategy.application.PendingStrategyMatchRecord;
import com.hope.trading.market_intelligence.strategy.domain.ConditionResult;
import com.hope.trading.market_intelligence.strategy.domain.MatchedDirection;
import com.hope.trading.market_intelligence.strategy.domain.StrategyMatch;
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
 * Story 0011 persistence semantics against real Flyway-managed schema
 * (H2 PostgreSQL mode): round-trip fidelity, business-key idempotency,
 * authoritative uniqueness and independence from strategy_definitions rows.
 */
@SpringBootTest
@ActiveProfiles("test")
class StrategyMatchPersistenceTest {

    private static final Instant MATCHED = Instant.parse("2026-08-21T10:00:00Z");
    private static final Instant STORED = Instant.parse("2026-08-21T10:00:01Z");
    private static final UUID STRATEGY = UUID.fromString("0a10c7e2-9d1e-4f5a-b6c8-123456789001");
    private static final UUID MARKET = UUID.fromString("cccccccc-1111-2222-3333-444444444444");
    private static final UUID ANALYSIS = UUID.fromString("aaaaaaaa-1111-2222-3333-444444444444");
    private static final UUID OBSERVATION = UUID.fromString("bbbbbbbb-1111-2222-3333-444444444444");

    @Autowired com.hope.trading.market_intelligence.strategy.application.StrategyMatchRepository repository;
    @Autowired StrategyMatchPersister persister;

    private PendingStrategyMatchRecord pending(String digest) {
        return new PendingStrategyMatchRecord(STRATEGY, 1, MARKET, ANALYSIS,
                OBSERVATION, MatchedDirection.LONG, digest,
                List.of(new ConditionResult("directional_price_change", true,
                        new BigDecimal("469.88").toPlainString())),
                MATCHED);
    }

    @Test
    void persistsAndReloadsFaithfully() {
        var result = persister.persist(pending("digest-round-trip")).orElseThrow();
        assertThat(result.created()).isTrue();

        Optional<StrategyMatch> reloaded = repository.findById(
                result.match().matchId());
        assertThat(reloaded).isPresent();
        StrategyMatch match = reloaded.get();
        assertThat(match.strategyId().value()).isEqualTo(STRATEGY);
        assertThat(match.strategyVersion()).isEqualTo(1);
        assertThat(match.marketId()).isEqualTo(MARKET);
        assertThat(match.analysisExecutionId()).isEqualTo(ANALYSIS);
        assertThat(match.observationId()).isEqualTo(OBSERVATION);
        assertThat(match.direction()).isEqualTo(MatchedDirection.LONG);
        assertThat(match.contextDigest()).isEqualTo("digest-round-trip");
        assertThat(match.matchedAt()).isEqualTo(MATCHED);
        // storage time is distinct from semantic match time and never earlier
        assertThat(match.createdAt()).isAfterOrEqualTo(match.matchedAt());
        // exact condition results round trip, deterministic order
        assertThat(match.conditionResults())
                .containsExactly(new ConditionResult("directional_price_change", true,
                        new BigDecimal("469.88").toPlainString()));
    }

    @Test
    void strategyDefinitionRowIsNotRequired() {
        // no strategy_definitions row exists for STRATEGY: insert must succeed
        var result = persister.persist(pending("digest-no-fk")).orElseThrow();
        assertThat(repository.findById(result.match().matchId())).isPresent();
    }

    @Test
    void databaseUniquenessIsAuthoritative() {
        persister.persist(pending("digest-dup"));
        // raw second insert of same logical identity through the adapter must hit the constraint
        assertThatThrownBy(() -> repository.save(StrategyMatch.rehydrate(
                UUID.randomUUID(), new com.hope.trading.market_intelligence.strategy.domain.StrategyId(STRATEGY),
                1, MARKET, ANALYSIS, OBSERVATION, MatchedDirection.LONG,
                "digest-dup",
                List.of(new ConditionResult("c", true, "1")),
                MATCHED, STORED)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameLogicalEvaluationPersistsOnce() {
        UUID analysis = UUID.randomUUID();
        PendingStrategyMatchRecord record = new PendingStrategyMatchRecord(
                STRATEGY, 1, MARKET, analysis, OBSERVATION, MatchedDirection.LONG,
                "digest-idem",
                List.of(new ConditionResult("c", true,
                        BigDecimal.ONE.toPlainString())),
                MATCHED);
        var first = persister.persist(record).orElseThrow();
        var second = persister.persist(record).orElseThrow();
        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.match().matchId()).isEqualTo(first.match().matchId());
        assertThat(repository.findByAnalysisExecutionId(analysis)).hasSize(1);
    }

    @Test
    void distinctDigestOrAnalysisOrVersionYieldDistinctMatches() {
        persister.persist(pending("d1"));

        PendingStrategyMatchRecord otherDigest = new PendingStrategyMatchRecord(
                STRATEGY, 1, MARKET, ANALYSIS, OBSERVATION, MatchedDirection.LONG,
                "d2", List.of(), MATCHED);
        assertThat(persister.persist(otherDigest).orElseThrow().created()).isTrue();

        PendingStrategyMatchRecord otherAnalysis = new PendingStrategyMatchRecord(
                STRATEGY, 1, MARKET, UUID.randomUUID(), OBSERVATION,
                MatchedDirection.LONG, "d1", List.of(), MATCHED);
        assertThat(persister.persist(otherAnalysis).orElseThrow().created()).isTrue();

        PendingStrategyMatchRecord otherVersion = new PendingStrategyMatchRecord(
                STRATEGY, 2, MARKET, ANALYSIS, OBSERVATION, MatchedDirection.LONG,
                "d1", List.of(), MATCHED);
        assertThat(persister.persist(otherVersion).orElseThrow().created()).isTrue();
    }
}
