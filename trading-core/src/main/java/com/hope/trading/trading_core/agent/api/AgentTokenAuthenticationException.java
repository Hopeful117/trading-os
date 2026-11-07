package com.hope.trading.trading_core.agent.api;

public class AgentTokenAuthenticationException extends RuntimeException {
    public AgentTokenAuthenticationException() {
        super("Agent authentication failed");
    }
}
