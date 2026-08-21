package com.hope.trading.market_intelligence.application.execution;

public class AnalysisContextUnavailableException extends RuntimeException {
    private final String code;

    public AnalysisContextUnavailableException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
