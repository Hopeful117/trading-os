package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcome;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcomeIdentity;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

public interface ObservedMarketOutcomeRepository {
    Optional<ObservedMarketOutcome> findByFingerprint(String fingerprint);

    /**
     * Resolves one explicitly selected immutable outcome version. Implementations
     * must not choose between corrections or depend on database row ordering.
     */
    Optional<ObservedMarketOutcome> findByIdentity(ObservedMarketOutcomeIdentity identity);

    List<ObservedMarketOutcome> findByMarketAndCutoffAndHorizon(
            UUID marketId, Instant evaluationCutoff, long horizonSeconds);

    List<ObservedMarketOutcome> findByMarkets(Set<UUID> marketIds);

    List<ObservedMarketOutcome> findByMarketsAndCutoffRange(
            Set<UUID> marketIds, Instant startCutoffInclusive, Instant endCutoffExclusive);

    ObservedMarketOutcome saveIfAbsent(ObservedMarketOutcome outcome);
}
