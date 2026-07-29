package com.hope.trading.market_intelligence.domain.capability;

public interface CancellationToken {
    boolean isCancellationRequested();

    default void throwIfCancellationRequested() {
        if (isCancellationRequested()) throw new CapabilityCancelledException();
    }
}
