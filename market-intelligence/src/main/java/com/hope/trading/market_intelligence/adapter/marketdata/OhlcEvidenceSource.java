package com.hope.trading.market_intelligence.adapter.marketdata;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OhlcEvidenceSource {
    OhlcEvidenceBatch acquire(UUID marketId, String interval, int limit, Instant cutoff);

    default OhlcEvidenceBatch acquire(
            UUID marketId, String interval, int limit, Instant cutoff,
            String selectionBinding, String role) {
        return acquire(marketId, interval, limit, cutoff);
    }

    default OhlcEvidenceBatch acquire(
            UUID marketId, String interval, int limit, Instant rangeStart, Instant cutoff,
            String selectionBinding, String role) {
        return acquire(marketId, interval, limit, cutoff, selectionBinding, role);
    }

    record OhlcEvidenceBatch(
            List<OhlcResponse> observations,
            String evidenceFingerprint,
            String normalizationVersion,
            int observationCount
    ) {
        public OhlcEvidenceBatch {
            observations = observations == null ? List.of() : List.copyOf(observations);
        }
    }
}
