package com.hope.trading.trading_core.execution.application.port;

public interface ExecutionMetrics {
    void executionCreated();
    void executionSucceeded();
    void executionFailed();
    void executionCancelled();
    void duplicatePrevented();
    void retryScheduled();
    void recoveryStarted();
    void unknownSubmission();
}
