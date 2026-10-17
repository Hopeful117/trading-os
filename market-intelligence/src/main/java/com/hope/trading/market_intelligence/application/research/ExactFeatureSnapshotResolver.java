package com.hope.trading.market_intelligence.application.research;

import com.hope.trading.market_intelligence.application.port.FeatureSnapshotRepository;
import com.hope.trading.market_intelligence.domain.feature.FeatureSnapshot;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Resolves only the exact immutable snapshot named by a candidate. */
@Service
public final class ExactFeatureSnapshotResolver {
    private final FeatureSnapshotRepository snapshots;

    public ExactFeatureSnapshotResolver(FeatureSnapshotRepository snapshots) {
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
    }

    public FeatureSnapshot resolve(UUID marketId, Instant cutoff, String projectionVersion,
                                   String contextFingerprint, String snapshotFingerprint,
                                   String identityFingerprint) {
        FeatureSnapshot snapshot = snapshots.findByFingerprint(snapshotFingerprint)
                .orElseThrow(() -> new IllegalArgumentException("referenced FeatureSnapshot is unavailable"));
        if (!snapshot.marketId().equals(marketId) || !snapshot.evaluationCutoff().equals(cutoff)
                || !snapshot.featureProjectionVersion().equals(projectionVersion)
                || !snapshot.contextFingerprint().equals(contextFingerprint)
                || !snapshot.fingerprint().equals(snapshotFingerprint)
                || !snapshot.identityFingerprint().equals(identityFingerprint)) {
            throw new IllegalArgumentException("referenced FeatureSnapshot does not match candidate identity");
        }
        return snapshot;
    }
}
