package com.hope.trading.market_intelligence.strategy.application;

import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluation;
import com.hope.trading.market_intelligence.strategy.domain.StrategyMatch;
import com.hope.trading.market_intelligence.strategy.domain.StrategyMatchIdentity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Idempotent required-truth persistence of one StrategyMatch (Story 0012).
 *
 * <p>Default REQUIRED propagation: when invoked inside the production pipeline
 * transaction (T1), the match participates atomically with Observation,
 * TradingOpportunity and PipelineRun. There is deliberately no REQUIRES_NEW
 * and no after-commit path anymore: a match can never commit without its full
 * T1 context, nor be left behind by a rollback.</p>
 *
 * <p>Idempotency: an existing row for the same logical identity is returned
 * (ALREADY_EXISTS); a concurrent duplicate insert resolves through the
 * authoritative database unique constraint and maps back to the existing row.</p>
 */
@Service
public class StrategyMatchPersister {

    private final StrategyMatchRepository repository;
    private final Clock clock;

    public StrategyMatchPersister(StrategyMatchRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    @Transactional
    public Optional<StrategyMatchPersistResult> persist(
            StrategyEvaluation evaluation,
            UUID analysisExecutionId,
            UUID observationId
    ) {
        Objects.requireNonNull(evaluation, "evaluation is required");
        if (!evaluation.isMatch()) {
            return Optional.empty();
        }
        StrategyMatchIdentity identity = new StrategyMatchIdentity(
                evaluation.strategyId().value(), evaluation.strategyVersion(),
                evaluation.marketId(), analysisExecutionId, evaluation.contextDigest());
        Optional<StrategyMatch> existing = repository.findByIdentity(identity);
        if (existing.isPresent()) {
            return existing.map(StrategyMatchPersistResult::alreadyExists);
        }
        try {
            StrategyMatch saved = repository.save(StrategyMatch.fromEvaluation(
                    evaluation, analysisExecutionId, observationId,
                    UUID.randomUUID(), clock.instant()));
            return Optional.of(StrategyMatchPersistResult.created(saved));
        } catch (DataIntegrityViolationException race) {
            return repository.findByIdentity(identity)
                    .map(StrategyMatchPersistResult::alreadyExists);
        }
    }
}
