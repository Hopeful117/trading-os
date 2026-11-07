package com.hope.trading.trading_core.agent.application;

public class AgentGrantException extends RuntimeException {
    private final int status;
    private final String code;

    public AgentGrantException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() { return status; }
    public String code() { return code; }
}
