package com.hope.trading.trading_core.execution.domain.exception;

public class DuplicateExecutionException extends RuntimeException {
    public DuplicateExecutionException() { super("A logical execution already exists"); }
}
