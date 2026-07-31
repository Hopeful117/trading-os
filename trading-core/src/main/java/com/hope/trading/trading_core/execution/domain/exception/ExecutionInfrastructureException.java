package com.hope.trading.trading_core.execution.domain.exception;

public class ExecutionInfrastructureException extends RuntimeException {
    public ExecutionInfrastructureException(String message, Throwable cause) { super(message, cause); }
}
