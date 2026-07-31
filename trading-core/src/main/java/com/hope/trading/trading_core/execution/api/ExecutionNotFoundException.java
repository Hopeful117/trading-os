package com.hope.trading.trading_core.execution.api;

public class ExecutionNotFoundException extends RuntimeException {
    public ExecutionNotFoundException(){super("Execution not found");}
}
