package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcome;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ObservedMarketOutcomeRepository {
    Optional<ObservedMarketOutcome> findByFingerprint(String fingerprint);

    List<ObservedMarketOutcome> findByMarketAndCutoffAndHorizon(
            UUID marketId, Instant evaluationCutoff, long horizonSeconds);

    ObservedMarketOutcome saveIfAbsent(ObservedMarketOutcome outcome);
}
