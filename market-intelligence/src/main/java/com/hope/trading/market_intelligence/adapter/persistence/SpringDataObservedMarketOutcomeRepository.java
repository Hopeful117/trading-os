package com.hope.trading.market_intelligence.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;

interface SpringDataObservedMarketOutcomeRepository
        extends JpaRepository<JpaObservedMarketOutcomeEntity, UUID> {
    Optional<JpaObservedMarketOutcomeEntity> findByOutcomeFingerprint(String outcomeFingerprint);

    List<JpaObservedMarketOutcomeEntity> findByMarketIdAndEvaluationCutoffAndHorizonSeconds(
            UUID marketId, Instant evaluationCutoff, long horizonSeconds);

    List<JpaObservedMarketOutcomeEntity> findByMarketIdIn(Collection<UUID> marketIds);

    List<JpaObservedMarketOutcomeEntity> findByMarketIdInAndEvaluationCutoffGreaterThanEqualAndEvaluationCutoffLessThan(
            Collection<UUID> marketIds, Instant startCutoffInclusive, Instant endCutoffExclusive);
}
