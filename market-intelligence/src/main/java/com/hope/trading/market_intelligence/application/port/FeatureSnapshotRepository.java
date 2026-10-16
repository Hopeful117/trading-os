package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.domain.feature.FeatureSnapshot;

import java.util.Optional;

public interface FeatureSnapshotRepository {
    Optional<FeatureSnapshot> findByFingerprint(String fingerprint);

    FeatureSnapshot saveIfAbsent(FeatureSnapshot snapshot);
}
