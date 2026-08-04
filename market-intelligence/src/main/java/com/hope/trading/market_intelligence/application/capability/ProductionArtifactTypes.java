package com.hope.trading.market_intelligence.application.capability;

import com.hope.trading.market_intelligence.domain.capability.ArtifactType;
import com.hope.trading.market_intelligence.domain.capability.ArtifactVersion;

public final class ProductionArtifactTypes {
    public static final ArtifactType MARKET_SNAPSHOT = new ArtifactType("normalized-market-snapshot");
    public static final ArtifactType OHLC_HISTORY = new ArtifactType("normalized-ohlc-history");
    public static final ArtifactType SPREAD_ANALYSIS = new ArtifactType("spread-analysis-result");
    public static final ArtifactType OHLC_RANGE_ANALYSIS = new ArtifactType("ohlc-range-analysis-result");
    public static final ArtifactVersion V1 = new ArtifactVersion("1.0.0");

    private ProductionArtifactTypes() {
    }
}
