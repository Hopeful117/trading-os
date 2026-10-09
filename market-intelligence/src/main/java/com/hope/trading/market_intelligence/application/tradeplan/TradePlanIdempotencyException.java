package com.hope.trading.market_intelligence.application.tradeplan;

public final class TradePlanIdempotencyException extends RuntimeException {
    private final String code;
    private final int status;

    public TradePlanIdempotencyException(String code, String message, int status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() { return code; }
    public int status() { return status; }
}
