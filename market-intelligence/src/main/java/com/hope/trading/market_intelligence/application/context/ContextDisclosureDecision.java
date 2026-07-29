package com.hope.trading.market_intelligence.application.context;

public record ContextDisclosureDecision(boolean allowed, String reason) {
    public static ContextDisclosureDecision allow() {
        return new ContextDisclosureDecision(true, "AUTHORIZED");
    }

    public static ContextDisclosureDecision deny(String reason) {
        return new ContextDisclosureDecision(false, reason);
    }
}
