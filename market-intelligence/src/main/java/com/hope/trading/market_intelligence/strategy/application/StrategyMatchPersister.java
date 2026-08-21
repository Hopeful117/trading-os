package com.hope.trading.market_intelligence.strategy.application;

import com.hope.trading.market_intelligence.strategy.domain.StrategyMatch;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Idempotent, independently transactional persistence of one pending
 * StrategyMatch record. Runs in its own REQUIRES_NEW transaction (T2) and is
 * only ever invoked from an after-commit callback, so a match is durably
 * written only after the pipeline evidence has committed (T1).
 *
 * <p>Idempotency: an existing row for the same logical identity returns
 {@link StrategyMatchPersistResult#alreadyExists()}; a concurrent duplicate
 * insert resolves through the authoritative database unique constraint and is
 * mapped back to the existing row.</p>
 */
@Service
public class StrategyMatchPersister {

    private final StrategyMatchRepository repository;
    private final Clock clock;

    public StrategyMatchPersister(StrategyMatchRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<StrategyMatchPersistResult> persist(PendingStrategyMatchRecord pending) {
        Objects.requireNonNull(pending, "pending record is required");
        Optional<StrategyMatch> existing = repository.findByIdentity(pending.identity());
        if (existing.isPresent()) {
            return existing.map(StrategyMatchPersistResult::alreadyExists);
        }
        try {
            StrategyMatch saved = repository.save(toMatch(pending));
            return Optional.of(StrategyMatchPersistResult.created(saved));
        } catch (DataIntegrityViolationException race) {
            return repository.findByIdentity(pending.identity())
                    .map(StrategyMatchPersistResult::alreadyExists);
        }
    }

    private StrategyMatch toMatch(PendingStrategyMatchRecord pending) {
        return StrategyMatch.fromEvaluationFields(
                pending.strategyId(),
                pending.strategyVersion(),
                pending.marketId(),
                pending.analysisExecutionId(),
                pending.observationId(),
                pending.direction(),
                pending.contextDigest(),
                pending.conditionResults(),
                pending.matchedAt(),
                UUID.randomUUID(),
                clock.instant());
    }
}
