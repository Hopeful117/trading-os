package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.application.port.ObservedMarketOutcomeRepository;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcome;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcomeIdentity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcomeStatus;

@Repository
public class JpaObservedMarketOutcomeRepository implements ObservedMarketOutcomeRepository {
    private final SpringDataObservedMarketOutcomeRepository repository;
    private final TransactionTemplate transactions;
    private final JdbcTemplate jdbc;

    public JpaObservedMarketOutcomeRepository(SpringDataObservedMarketOutcomeRepository repository,
                                              PlatformTransactionManager transactionManager,
                                              JdbcTemplate jdbc) {
        this.repository = repository;
        this.transactions = new TransactionTemplate(transactionManager);
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ObservedMarketOutcome> findByFingerprint(String fingerprint) {
        return repository.findByOutcomeFingerprint(fingerprint).map(JpaObservedMarketOutcomeEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ObservedMarketOutcome> findByIdentity(ObservedMarketOutcomeIdentity identity) {
        return repository.findByOutcomeFingerprint(identity.outcomeFingerprint())
                .map(JpaObservedMarketOutcomeEntity::toDomain)
                .filter(identity::matches);
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
    @Transactional(readOnly = true)
    public List<ObservedMarketOutcome> findByMarkets(Set<UUID> marketIds) {
        if (marketIds.isEmpty()) {
            return List.of();
        }
        return repository.findByMarketIdIn(marketIds).stream()
                .map(JpaObservedMarketOutcomeEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ObservedMarketOutcome> findByMarketsAndCutoffRange(
            Set<UUID> marketIds, Instant startCutoffInclusive, Instant endCutoffExclusive) {
        if (marketIds.isEmpty()) {
            return List.of();
        }
        return repository.findByMarketIdInAndEvaluationCutoffGreaterThanEqualAndEvaluationCutoffLessThan(
                        marketIds, startCutoffInclusive, endCutoffExclusive).stream()
                .map(JpaObservedMarketOutcomeEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ObservedMarketOutcome> findByMarketsAndCutoffRange(
            Set<UUID> marketIds, Instant startCutoffInclusive, Instant endCutoffExclusive, int limit) {
        if (marketIds.isEmpty()) return List.of();
        return repository.findByMarketIdInAndEvaluationCutoffGreaterThanEqualAndEvaluationCutoffLessThanOrderByEvaluationCutoffAsc(
                        marketIds, startCutoffInclusive, endCutoffExclusive, PageRequest.of(0, limit)).stream()
                .map(JpaObservedMarketOutcomeEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countByMarketsAndCutoffRangeAndStatus(Set<UUID> marketIds, Instant startCutoffInclusive,
                                                      Instant endCutoffExclusive, ObservedMarketOutcomeStatus status) {
        if (marketIds.isEmpty()) return 0;
        return repository.countByMarketIdInAndEvaluationCutoffGreaterThanEqualAndEvaluationCutoffLessThanAndStatus(
                marketIds, startCutoffInclusive, endCutoffExclusive, status.name());
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

    @Override
    public ObservedMarketOutcome saveIfAbsentOwned(ObservedMarketOutcome outcome, UUID leaseId) {
        try {
            return transactions.execute(status -> {
                requireLease(outcome.analysisExecutionId(), leaseId);
                Optional<JpaObservedMarketOutcomeEntity> existing =
                        repository.findByOutcomeFingerprint(outcome.outcomeFingerprint());
                if (existing.isPresent()) {
                    if (!existing.get().hasSameSemanticContent(outcome)) {
                        throw new IllegalStateException("Outcome fingerprint has conflicting content");
                    }
                    return existing.get().toDomain();
                }
                return repository.saveAndFlush(JpaObservedMarketOutcomeEntity.from(outcome)).toDomain();
            });
        } catch (DataIntegrityViolationException race) {
            return repository.findByOutcomeFingerprint(outcome.outcomeFingerprint())
                    .filter(entity -> entity.hasSameSemanticContent(outcome))
                    .map(JpaObservedMarketOutcomeEntity::toDomain)
                    .orElseThrow(() -> new IllegalStateException(
                            "Concurrent owned outcome insert has conflicting content", race));
        }
    }

    private void requireLease(UUID executionId, UUID leaseId) {
        try {
            UUID currentLease = jdbc.queryForObject("""
                    select lease_id from historical_reconstruction_lifecycles
                    where execution_id = ? and status = 'RUNNING'
                    for update
                    """, UUID.class, executionId);
            if (!leaseId.equals(currentLease)) {
                throw new IllegalStateException("Historical reconstruction lifecycle lease was lost before outcome persistence");
            }
        } catch (EmptyResultDataAccessException missing) {
            throw new IllegalStateException("Historical reconstruction lifecycle lease was lost before outcome persistence");
        }
    }
}
