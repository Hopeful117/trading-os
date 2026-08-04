package com.hope.trading.trading_core.tradeplanning.application;

public final class TradePlanningProfileException extends RuntimeException {
    private final String code;
    private final int status;
    public TradePlanningProfileException(String code, String message, int status) {
        super(message); this.code = code; this.status = status;
    }
    public String code() { return code; }
    public int status() { return status; }
}
