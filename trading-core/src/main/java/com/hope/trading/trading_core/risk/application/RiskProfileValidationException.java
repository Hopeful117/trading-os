package com.hope.trading.trading_core.risk.application;

public class RiskProfileValidationException extends RuntimeException {
    private final String code;

    public RiskProfileValidationException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
