package com.hope.trading.market_intelligence.application.capability;

import com.hope.trading.market_intelligence.domain.capability.ArtifactType;
import com.hope.trading.market_intelligence.domain.capability.ArtifactVersion;

public final class ProductionArtifactTypes {
    public static final ArtifactType MARKET_SNAPSHOT = new ArtifactType("normalized-market-snapshot");
    public static final ArtifactType OHLC_HISTORY = new ArtifactType("normalized-ohlc-history");
    public static final ArtifactType SPREAD_ANALYSIS = new ArtifactType("spread-analysis-result");
    public static final ArtifactType OHLC_RANGE_ANALYSIS = new ArtifactType("ohlc-range-analysis-result");
    public static final ArtifactType OHLC_MOMENTUM_ANALYSIS = new ArtifactType("ohlc-momentum-analysis-result");
    public static final ArtifactType TREND_CONTEXT_HISTORY = new ArtifactType("trend-context-history");
    public static final ArtifactType MARKET_STRUCTURE = new ArtifactType("market-structure");
    public static final ArtifactType TREND_CONTEXT_ASSESSMENT = new ArtifactType("trend-context-assessment");
    public static final ArtifactType ORDER_BOOK_REPLAY = new ArtifactType("order-book-replay");
    public static final ArtifactType ORDER_BOOK_LIQUIDITY = new ArtifactType("order-book-liquidity");
    public static final ArtifactType NEWS_CONTEXT = new ArtifactType("news-context");
    public static final ArtifactType TRADEABILITY_ASSESSMENT = new ArtifactType("tradeability-assessment");
    public static final ArtifactVersion V1 = new ArtifactVersion("1.0.0");
    public static final ArtifactVersion MARKET_STRUCTURE_V2 = new ArtifactVersion("2.0.0");
    public static final ArtifactVersion ORDER_BOOK_LIQUIDITY_V1 = new ArtifactVersion("1.0.0");

    private ProductionArtifactTypes() {
    }
}
