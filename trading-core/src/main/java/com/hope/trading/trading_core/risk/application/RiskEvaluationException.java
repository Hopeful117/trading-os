package com.hope.trading.trading_core.risk.application;

public final class RiskEvaluationException extends RuntimeException {
    private final String code;
    private final int status;

    public RiskEvaluationException(String code, String message, int status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() { return code; }
    public int status() { return status; }
}
