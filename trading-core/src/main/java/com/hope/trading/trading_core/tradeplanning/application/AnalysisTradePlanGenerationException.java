package com.hope.trading.trading_core.tradeplanning.application;

import java.util.Map;

public class AnalysisTradePlanGenerationException extends RuntimeException {
    private final String code;
    private final int status;
    private final Map<String, Object> details;

    public AnalysisTradePlanGenerationException(String code, String message, int status) {
        this(code, message, status, Map.of());
    }

    public AnalysisTradePlanGenerationException(String code, String message, int status,
                                                Map<String, Object> details) {
        super(message);
        this.code = code;
        this.status = status;
        this.details = Map.copyOf(details);
    }
    public String code() { return code; }
    public int status() { return status; }
    public Map<String, Object> details() { return details; }
}
