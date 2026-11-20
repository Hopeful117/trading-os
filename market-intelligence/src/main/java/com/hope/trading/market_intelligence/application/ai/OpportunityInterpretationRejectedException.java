package com.hope.trading.market_intelligence.application.ai;

public final class OpportunityInterpretationRejectedException extends RuntimeException {
    private final String code;
    private final int status;

    public OpportunityInterpretationRejectedException(String code) {
        this(code, defaultStatus(code));
    }

    public OpportunityInterpretationRejectedException(String code, int status) {
        super(code);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public int status() {
        return status;
    }

    private static int defaultStatus(String code) {
        return switch (code) {
            case "OPPORTUNITY_NOT_AUTHORIZED", "OPPORTUNITY_VERSION_NOT_FOUND", "MARKET_THESIS_NOT_FOUND" -> 404;
            case "OPPORTUNITY_VERSION_SUPERSEDED" -> 409;
            default -> 422;
        };
    }
}
