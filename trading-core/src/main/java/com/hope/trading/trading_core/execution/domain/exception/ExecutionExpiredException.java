package com.hope.trading.trading_core.execution.domain.exception;

public class ExecutionExpiredException extends RuntimeException {
    public ExecutionExpiredException() { super("Execution intent has expired"); }
}
