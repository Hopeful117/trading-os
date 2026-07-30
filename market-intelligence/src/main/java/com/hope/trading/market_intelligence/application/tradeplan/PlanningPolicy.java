package com.hope.trading.market_intelligence.application.tradeplan;

public interface PlanningPolicy {
    String id();
    int order();
    boolean supports(PlanningInput input);
    PlanningContribution evaluate(PlanningInput input);
}
