package com.hope.trading.gateway.security;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentJwtServiceTest {
    @Test
    void rejectsInvalidAgentTokenWithoutFallingBackToHumanClaims() {
        AgentJwtProperties properties = new AgentJwtProperties();
        properties.setSecret("Y2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2Nj");
        properties.setIssuer("trading-os-agent-test");
        properties.setAudience("trading-os-gateway-test");
        AgentJwtService service = new AgentJwtService(properties);

        assertThatThrownBy(() -> service.validate("eyJhbGciOiJIUzI1NiJ9.invalid.signature"))
                .isInstanceOf(RuntimeException.class);
    }
}
