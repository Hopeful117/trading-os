package com.hope.trading.market_intelligence.domain.planning;

public class ExecutionPlanningException extends RuntimeException {
    private final PlanningFailure failure;
    public ExecutionPlanningException(PlanningFailure failure) {
        super(failure.message());
        this.failure = failure;
    }
    public PlanningFailure failure() { return failure; }
}
