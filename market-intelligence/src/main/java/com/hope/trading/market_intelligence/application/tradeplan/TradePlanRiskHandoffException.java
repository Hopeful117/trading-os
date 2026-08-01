package com.hope.trading.market_intelligence.application.tradeplan;

public final class TradePlanRiskHandoffException extends RuntimeException {
    private final String code;
    private final int status;

    private TradePlanRiskHandoffException(String code, int status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static TradePlanRiskHandoffException notFound(String message) {
        return new TradePlanRiskHandoffException("TRADE_PLAN_RISK_SNAPSHOT_NOT_FOUND", 404, message);
    }

    public static TradePlanRiskHandoffException conflict(String code, String message) {
        return new TradePlanRiskHandoffException(code, 409, message);
    }

    public static TradePlanRiskHandoffException invalidDecision(String message) {
        return new TradePlanRiskHandoffException("RISK_DECISION_NOT_APPROVED", 422, message);
    }

    public String code() { return code; }

    public int status() { return status; }
}
