package com.hope.trading.market_intelligence.domain.tradeplan;

/** Trade planning lifecycle, independent from risk and execution implementations. */
public enum TradePlanStatus {
    DRAFT, PROPOSED, ACCEPTED, REJECTED, EXPIRED,
    RISK_VALIDATED, READY_TO_EXECUTE, EXECUTED
}
