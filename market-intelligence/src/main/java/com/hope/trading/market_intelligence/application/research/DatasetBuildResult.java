package com.hope.trading.market_intelligence.application.research;

import com.hope.trading.market_intelligence.domain.research.DatasetManifest;
import com.hope.trading.market_intelligence.domain.research.ResearchSample;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record DatasetBuildResult(
        DatasetManifest manifest,
        List<ResearchSample> samples,
        Map<String, Instant> featureEvidenceKnownAtBySnapshotFingerprint
) {
    public DatasetBuildResult(DatasetManifest manifest, List<ResearchSample> samples) {
        this(manifest, samples, Map.of());
    }

    public DatasetBuildResult {
        samples = List.copyOf(samples);
        featureEvidenceKnownAtBySnapshotFingerprint = Map.copyOf(featureEvidenceKnownAtBySnapshotFingerprint);
    }
}
