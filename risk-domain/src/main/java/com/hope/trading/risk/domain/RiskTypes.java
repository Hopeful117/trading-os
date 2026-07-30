package com.hope.trading.risk.domain;

/**
 * Stable vocabulary used by the deterministic risk domain.
 */
public final class RiskTypes {
    private RiskTypes() {}

    public enum RiskDecision { APPROVED, APPROVED_WITH_WARNINGS, REJECTED }
    public enum EvaluationStatus { COMPLETED, INCOMPLETE, FAILED }
    public enum ValidationMode {
        PRE_TRADE, POSITION_MONITORING, PORTFOLIO_MONITORING,
        ACCOUNT_MONITORING, SIMULATION
    }
    public enum RuleSeverity { INFO, WARNING, BLOCKING }
    public enum RuleStatus { PASS, WARNING, FAILURE, NOT_APPLICABLE }
    public enum RuleCategory {
        POSITION, ACCOUNT, PORTFOLIO, PROP_FIRM, SESSION, MARKET, COMPLIANCE, CUSTOM
    }
    public enum PolicyAuthority {
        PLATFORM(0), BROKER(1), PROP_FIRM(2), ACCOUNT(3), USER(4), STRATEGY(5);
        private final int rank;
        PolicyAuthority(int rank) { this.rank = rank; }
        public int rank() { return rank; }
    }
    public enum TradeDirection { LONG, SHORT }
}
