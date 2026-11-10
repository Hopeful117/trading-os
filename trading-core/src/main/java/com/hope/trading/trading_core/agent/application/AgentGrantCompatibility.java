package com.hope.trading.trading_core.agent.application;

import com.hope.trading.trading_core.agent.model.AgentPermission;
import com.hope.trading.trading_core.agent.model.AgentResourceType;

import java.util.Map;
import java.util.Set;

final class AgentGrantCompatibility {
    private static final Map<AgentPermission, Set<AgentResourceType>> SUPPORTED = Map.ofEntries(
            Map.entry(AgentPermission.ACCOUNTS_READ, Set.of(AgentResourceType.BROKER_ACCOUNT)),
            Map.entry(AgentPermission.ACCOUNTS_SYNC, Set.of(AgentResourceType.BROKER_ACCOUNT)),
            Map.entry(AgentPermission.POSITIONS_READ, Set.of(AgentResourceType.BROKER_ACCOUNT)),
            Map.entry(AgentPermission.MARKETS_READ, Set.of(AgentResourceType.MARKET)),
            Map.entry(AgentPermission.RISK_READ, Set.of(AgentResourceType.BROKER_ACCOUNT)),
            Map.entry(AgentPermission.RESEARCH_RUN, Set.of(AgentResourceType.RESEARCH_SCOPE)),
            Map.entry(AgentPermission.RESEARCH_INSPECT, Set.of(AgentResourceType.BROKER_ACCOUNT)),
            Map.entry(AgentPermission.RESEARCH_EXPORT, Set.of(AgentResourceType.RESEARCH_SCOPE)),
            Map.entry(AgentPermission.PLANS_CREATE, Set.of(AgentResourceType.BROKER_ACCOUNT)),
            Map.entry(AgentPermission.TRADE_PLAN_APPROVAL_REQUEST, Set.of(AgentResourceType.BROKER_ACCOUNT)),
            Map.entry(AgentPermission.TRADE_PLAN_RISK_EVALUATE, Set.of(AgentResourceType.BROKER_ACCOUNT)),
            Map.entry(AgentPermission.TRADE_PLAN_APPROVAL_STATUS_READ, Set.of(AgentResourceType.BROKER_ACCOUNT)));

    private AgentGrantCompatibility() {
    }

    static boolean supports(AgentPermission permission, AgentResourceType resourceType) {
        return SUPPORTED.getOrDefault(permission, Set.of()).contains(resourceType);
    }
}
