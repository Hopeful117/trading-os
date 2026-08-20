package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.domain.scan.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActiveScanRepository {
    ActiveScan save(ActiveScan scan);

    List<ActiveScanMarket> saveMarkets(List<ActiveScanMarket> markets);

    Optional<ActiveScan> findByActorIdAndIdempotencyKey(UUID actorId, String idempotencyKey);

    Optional<ActiveScan> findByActorIdAndScanId(UUID actorId, UUID scanId);

    Optional<ActiveScan> findById(UUID scanId);

    List<ActiveScanMarket> findMarketsByScanId(UUID scanId);

    Optional<ActiveScanMarket> findMarketById(UUID scanMarketId);

    boolean transitionScanStatus(
            UUID scanId,
            ActiveScanStatus expected,
            ActiveScanStatus target,
            Instant updatedAt
    );

    boolean transitionMarketStatus(
            UUID scanMarketId,
            ActiveScanMarketStatus expected,
            ActiveScanMarketStatus target,
            Instant updatedAt
    );
}
