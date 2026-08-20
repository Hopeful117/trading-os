package com.hope.trading.market_intelligence.application.scan;

public class ActiveScanException extends RuntimeException {
    private final String code;
    private final int status;

    public ActiveScanException(String code, String message, int status) {
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
