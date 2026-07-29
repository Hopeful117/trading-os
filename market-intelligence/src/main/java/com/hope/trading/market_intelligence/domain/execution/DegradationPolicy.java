package com.hope.trading.market_intelligence.domain.execution;

public record DegradationPolicy(
        boolean dropOptionalCapabilities,
        boolean reduceContextDepth,
        boolean acceptPartialResult,
        boolean acceptDegradedResult
) {
}
