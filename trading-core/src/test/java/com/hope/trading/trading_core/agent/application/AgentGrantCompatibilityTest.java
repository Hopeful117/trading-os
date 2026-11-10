package com.hope.trading.trading_core.agent.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.trading_core.agent.model.AgentPermission;
import com.hope.trading.trading_core.agent.model.AgentResourceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentGrantCompatibilityTest {
    @Test
    void accountPermissionsRequireBrokerAccountScope() {
        assertThat(AgentGrantCompatibility.supports(AgentPermission.ACCOUNTS_READ,
                AgentResourceType.BROKER_ACCOUNT)).isTrue();
        assertThat(AgentGrantCompatibility.supports(AgentPermission.ACCOUNTS_READ,
                AgentResourceType.MARKET)).isFalse();
    }

    @Test
    void marketPermissionRequiresMarketScope() {
        assertThat(AgentGrantCompatibility.supports(AgentPermission.MARKETS_READ,
                AgentResourceType.MARKET)).isTrue();
        assertThat(AgentGrantCompatibility.supports(AgentPermission.MARKETS_READ,
                AgentResourceType.BROKER_ACCOUNT)).isFalse();
    }

    @Test
    void tradePlanRiskEvaluationRequiresBrokerAccountScope() {
        assertThat(AgentGrantCompatibility.supports(AgentPermission.TRADE_PLAN_RISK_EVALUATE,
                AgentResourceType.BROKER_ACCOUNT)).isTrue();
        assertThat(AgentGrantCompatibility.supports(AgentPermission.TRADE_PLAN_RISK_EVALUATE,
                AgentResourceType.MARKET)).isFalse();
    }

    @Test
    void historicalResearchPermissionsUseTheirDeclaredResourceScopes() {
        assertThat(AgentGrantCompatibility.supports(AgentPermission.RESEARCH_INSPECT,
                AgentResourceType.BROKER_ACCOUNT)).isTrue();
        assertThat(AgentGrantCompatibility.supports(AgentPermission.RESEARCH_INSPECT,
                AgentResourceType.RESEARCH_SCOPE)).isFalse();
        assertThat(AgentGrantCompatibility.supports(AgentPermission.RESEARCH_EXPORT,
                AgentResourceType.RESEARCH_SCOPE)).isTrue();
        assertThat(AgentGrantCompatibility.supports(AgentPermission.RESEARCH_EXPORT,
                AgentResourceType.BROKER_ACCOUNT)).isFalse();
    }

    @Test
    void usesStablePermissionIdentifiersAtTheJsonBoundary() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        assertThat(mapper.writeValueAsString(AgentPermission.ACCOUNTS_READ)).isEqualTo("\"accounts:read\"");
        assertThat(mapper.readValue("\"accounts:read\"", AgentPermission.class))
                .isEqualTo(AgentPermission.ACCOUNTS_READ);
    }
}
