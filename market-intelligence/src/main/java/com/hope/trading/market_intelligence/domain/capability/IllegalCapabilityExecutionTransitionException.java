package com.hope.trading.market_intelligence.domain.capability;

public class IllegalCapabilityExecutionTransitionException extends IllegalStateException {
    public IllegalCapabilityExecutionTransitionException(
            CapabilityExecutionState from, CapabilityExecutionState to) {
        super("Illegal capability execution transition from " + from + " to " + to);
    }
}
