package com.hope.trading.trading_core.agent.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum AgentPermission {
    ACCOUNTS_READ("accounts:read"),
    ACCOUNTS_SYNC("accounts:sync"),
    POSITIONS_READ("positions:read"),
    MARKETS_READ("markets:read"),
    RISK_READ("risk:read"),
    RESEARCH_RUN("research:run"),
    SCANS_RUN("scans:run"),
    RESEARCH_INSPECT("research:inspect"),
    RESEARCH_EXPORT("research:export"),
    PLANS_CREATE("plans:create"),
    TRADE_PLAN_RISK_EVALUATE("trade-plan-risk:evaluate"),
    TRADE_PLAN_APPROVAL_REQUEST("trade-plan-approval:request"),
    TRADE_PLAN_APPROVAL_STATUS_READ("trade-plan-approval:status:read"),
    EXECUTIONS_REQUEST("executions:request"),
    EXECUTIONS_CONTINUE_APPROVED("executions:continue-approved"),
    EXECUTIONS_STATUS_READ("executions:status:read");

    private final String value;

    AgentPermission(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static AgentPermission fromValue(String value) {
        return Arrays.stream(values())
                .filter(permission -> permission.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported agent permission"));
    }
}
