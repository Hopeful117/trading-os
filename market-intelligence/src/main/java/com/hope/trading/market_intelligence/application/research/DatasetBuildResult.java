package com.hope.trading.market_intelligence.application.research;

import com.hope.trading.market_intelligence.domain.research.DatasetManifest;
import com.hope.trading.market_intelligence.domain.research.ResearchSample;

import java.util.List;

public record DatasetBuildResult(DatasetManifest manifest, List<ResearchSample> samples) {
    public DatasetBuildResult {
        samples = List.copyOf(samples);
    }
}
