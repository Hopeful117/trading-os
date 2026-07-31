package com.hope.trading.trading_core.execution.domain.exception;

public class InvalidExecutionStateException extends RuntimeException {
    public InvalidExecutionStateException(String message) { super(message); }
}
