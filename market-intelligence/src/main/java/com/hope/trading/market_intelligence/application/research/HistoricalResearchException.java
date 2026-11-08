package com.hope.trading.market_intelligence.application.research;

public class HistoricalResearchException extends RuntimeException {
    private final int status;
    private final String code;

    public HistoricalResearchException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() { return status; }
    public String code() { return code; }
}
