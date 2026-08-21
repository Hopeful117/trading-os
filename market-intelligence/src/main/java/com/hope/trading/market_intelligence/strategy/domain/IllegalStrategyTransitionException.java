package com.hope.trading.market_intelligence.strategy.domain;

public class IllegalStrategyTransitionException extends RuntimeException {

    private final StrategyLifecycle from;
    private final StrategyLifecycle to;

    public IllegalStrategyTransitionException(StrategyLifecycle from, StrategyLifecycle to) {
        super("Illegal strategy lifecycle transition " + from + " -> " + to);
        this.from = from;
        this.to = to;
    }

    public StrategyLifecycle from() {
        return from;
    }

    public StrategyLifecycle to() {
        return to;
    }
}
