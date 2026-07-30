package com.hope.trading.market_intelligence.application.opportunity;

public record ExpirationDecision(boolean expire, ExpirationReason reason) {
    public static ExpirationDecision keep() {
        return new ExpirationDecision(false, ExpirationReason.NONE);
    }
    public static ExpirationDecision expire(ExpirationReason reason) {
        return new ExpirationDecision(true, reason);
    }
}
