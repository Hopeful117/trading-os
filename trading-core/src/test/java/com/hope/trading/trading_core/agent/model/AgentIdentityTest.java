package com.hope.trading.trading_core.agent.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentIdentityTest {
    private final UUID ownerId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-07T12:00:00Z");

    @Test
    void registrationAssignsImmutableOwnerAndActiveStatus() {
        AgentIdentity agent = AgentIdentity.register(ownerId, " OpenCode ", AgentType.OPENCODE, now);

        assertThat(agent.id()).isNotNull();
        assertThat(agent.ownerUserId()).isEqualTo(ownerId);
        assertThat(agent.displayName()).isEqualTo("OpenCode");
        assertThat(agent.status()).isEqualTo(AgentStatus.ACTIVE);
        assertThat(agent.createdAt()).isEqualTo(now);
    }

    @Test
    void revokedAgentCannotBeReactivated() {
        AgentIdentity agent = AgentIdentity.register(ownerId, "OpenCode", AgentType.OPENCODE, now);
        agent.revoke(now.plusSeconds(1));

        assertThatThrownBy(() -> agent.activate(now.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Revoked agent");
    }
}
