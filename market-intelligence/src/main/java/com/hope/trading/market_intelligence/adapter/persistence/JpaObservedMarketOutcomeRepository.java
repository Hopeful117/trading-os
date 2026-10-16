package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.application.port.ObservedMarketOutcomeRepository;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcome;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaObservedMarketOutcomeRepository implements ObservedMarketOutcomeRepository {
    private final SpringDataObservedMarketOutcomeRepository repository;

    public JpaObservedMarketOutcomeRepository(SpringDataObservedMarketOutcomeRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ObservedMarketOutcome> findByFingerprint(String fingerprint) {
        return repository.findByOutcomeFingerprint(fingerprint).map(JpaObservedMarketOutcomeEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ObservedMarketOutcome> findByMarketAndCutoffAndHorizon(
            UUID marketId, Instant evaluationCutoff, long horizonSeconds) {
        return repository.findByMarketIdAndEvaluationCutoffAndHorizonSeconds(
                        marketId, evaluationCutoff, horizonSeconds).stream()
                .map(JpaObservedMarketOutcomeEntity::toDomain)
                .toList();
    }

    @Override
    public ObservedMarketOutcome saveIfAbsent(ObservedMarketOutcome outcome) {
        Optional<JpaObservedMarketOutcomeEntity> existing =
                repository.findByOutcomeFingerprint(outcome.outcomeFingerprint());
        if (existing.isPresent()) {
            if (!existing.get().hasSameSemanticContent(outcome)) {
                throw new IllegalStateException("Outcome fingerprint has conflicting content");
            }
            return existing.get().toDomain();
        }
        try {
            return repository.saveAndFlush(JpaObservedMarketOutcomeEntity.from(outcome)).toDomain();
        } catch (DataIntegrityViolationException race) {
            return repository.findByOutcomeFingerprint(outcome.outcomeFingerprint())
                    .filter(entity -> entity.hasSameSemanticContent(outcome))
                    .map(JpaObservedMarketOutcomeEntity::toDomain)
                    .orElseThrow(() -> new IllegalStateException(
                            "Concurrent outcome insert has conflicting content", race));
        }
    }
}
