package com.hope.trading.trading_core.agent.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class AgentBusinessAuthorizationException extends RuntimeException {
    public AgentBusinessAuthorizationException() {
        super("Agent business authorization denied");
    }
}
