package com.hope.trading.trading_core.agent.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AgentAuthenticationAuditEventRepository
        extends JpaRepository<AgentAuthenticationAuditEventEntity, UUID> {
}
