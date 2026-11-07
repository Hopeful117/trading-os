package com.hope.trading.trading_core.agent.operation.infrastructure;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface AgentOperationAuditEventRepository extends JpaRepository<AgentOperationAuditEventEntity, UUID> {}
