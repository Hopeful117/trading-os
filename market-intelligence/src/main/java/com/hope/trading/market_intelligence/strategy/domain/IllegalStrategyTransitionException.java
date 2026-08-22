package com.hope.trading.market_intelligence.strategy.domain;

public class IllegalStrategyTransitionException extends RuntimeException {

    private final StrategyOperationalStatus from;
    private final StrategyOperationalStatus to;

    public IllegalStrategyTransitionException(
            StrategyOperationalStatus from, StrategyOperationalStatus to) {
        super("Illegal strategy operational status transition " + from + " -> " + to);
        this.from = from;
        this.to = to;
    }

    public StrategyOperationalStatus from() {
        return from;
    }

    public StrategyOperationalStatus to() {
        return to;
    }
}
