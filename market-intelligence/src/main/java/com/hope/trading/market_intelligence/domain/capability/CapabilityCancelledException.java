package com.hope.trading.market_intelligence.domain.capability;

public class CapabilityCancelledException extends RuntimeException {
    public CapabilityCancelledException() {
        super("Capability execution was cancelled");
    }
}
