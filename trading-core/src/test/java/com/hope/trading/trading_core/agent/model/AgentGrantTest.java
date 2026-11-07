package com.hope.trading.trading_core.agent.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentGrantTest {
    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");

    @Test
    void createsAnActiveExplicitGrant() {
        AgentGrant grant = AgentGrant.create(UUID.randomUUID(), AgentPermission.ACCOUNTS_READ,
                AgentResourceType.BROKER_ACCOUNT, UUID.randomUUID(), UUID.randomUUID(), NOW,
                NOW.plusSeconds(60));

        assertThat(grant.status()).isEqualTo(AgentGrantStatus.ACTIVE);
        assertThat(grant.effectiveAt(NOW.plusSeconds(30))).isTrue();
    }

    @Test
    void rejectsAnAlreadyExpiredGrant() {
        assertThatThrownBy(() -> AgentGrant.create(UUID.randomUUID(), AgentPermission.ACCOUNTS_READ,
                AgentResourceType.BROKER_ACCOUNT, UUID.randomUUID(), UUID.randomUUID(), NOW,
                NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void revocationIsTerminalAndIdempotent() {
        AgentGrant grant = AgentGrant.create(UUID.randomUUID(), AgentPermission.ACCOUNTS_READ,
                AgentResourceType.BROKER_ACCOUNT, UUID.randomUUID(), UUID.randomUUID(), NOW, null);

        grant.revoke(NOW.plusSeconds(1), "OWNER_REQUEST");
        grant.revoke(NOW.plusSeconds(2), "OWNER_REQUEST");

        assertThat(grant.status()).isEqualTo(AgentGrantStatus.REVOKED);
        assertThat(grant.effectiveAt(NOW.plusSeconds(2))).isFalse();
        assertThat(grant.revokedAt()).isEqualTo(NOW.plusSeconds(1));
    }
}
