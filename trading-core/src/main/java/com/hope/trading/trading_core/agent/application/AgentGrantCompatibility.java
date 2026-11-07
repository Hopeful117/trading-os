package com.hope.trading.trading_core.agent.application;

import com.hope.trading.trading_core.agent.model.AgentPermission;
import com.hope.trading.trading_core.agent.model.AgentResourceType;

import java.util.Map;
import java.util.Set;

final class AgentGrantCompatibility {
    private static final Map<AgentPermission, Set<AgentResourceType>> SUPPORTED = Map.of(
            AgentPermission.ACCOUNTS_READ, Set.of(AgentResourceType.BROKER_ACCOUNT),
            AgentPermission.ACCOUNTS_SYNC, Set.of(AgentResourceType.BROKER_ACCOUNT),
            AgentPermission.POSITIONS_READ, Set.of(AgentResourceType.BROKER_ACCOUNT),
            AgentPermission.MARKETS_READ, Set.of(AgentResourceType.MARKET),
            AgentPermission.RISK_READ, Set.of(AgentResourceType.BROKER_ACCOUNT),
            AgentPermission.RESEARCH_RUN, Set.of(AgentResourceType.RESEARCH_SCOPE),
            AgentPermission.PLANS_CREATE, Set.of(AgentResourceType.BROKER_ACCOUNT));

    private AgentGrantCompatibility() {
    }

    static boolean supports(AgentPermission permission, AgentResourceType resourceType) {
        return SUPPORTED.getOrDefault(permission, Set.of()).contains(resourceType);
    }
}
