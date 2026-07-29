package com.hope.trading.market_intelligence.domain.capability;

/** Atomic business analysis unit. It receives data, never infrastructure. */
public interface Capability {
    CapabilityMetadata metadata();
    CapabilityResult execute(CapabilityContext context);
}
