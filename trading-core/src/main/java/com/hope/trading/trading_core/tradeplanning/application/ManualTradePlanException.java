package com.hope.trading.trading_core.tradeplanning.application;

public final class ManualTradePlanException extends RuntimeException {
    private final String code;
    private final int status;

    public ManualTradePlanException(String code, String message, int status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public int status() {
        return status;
    }
}
