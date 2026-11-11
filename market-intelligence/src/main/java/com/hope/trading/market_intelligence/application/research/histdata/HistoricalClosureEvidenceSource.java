package com.hope.trading.market_intelligence.application.research.histdata;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Application seam for a future Market Data closure-evidence adapter.
 *
 * The current production implementation deliberately returns no evidence until
 * an approved authoritative source is available.
 */
@FunctionalInterface
public interface HistoricalClosureEvidenceSource {
    List<Evidence> find(Set<UUID> marketIds, Instant startCutoffInclusive,
                        Instant endCutoffExclusive);

    record Evidence(UUID evidenceId, UUID marketId, String canonicalSymbol, String interval,
                    String venue, String closureType, String reason, Instant closureStart,
                    Instant closureEnd, String sourceId, String sourceVersion,
                    Instant effectiveFrom, Instant effectiveTo, Instant knownAt,
                    String fingerprint) {
    }
}
