package com.hope.trading.market_intelligence.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataObservedMarketOutcomeRepository
        extends JpaRepository<JpaObservedMarketOutcomeEntity, UUID> {
    Optional<JpaObservedMarketOutcomeEntity> findByOutcomeFingerprint(String outcomeFingerprint);

    List<JpaObservedMarketOutcomeEntity> findByMarketIdAndEvaluationCutoffAndHorizonSeconds(
            UUID marketId, Instant evaluationCutoff, long horizonSeconds);
}
