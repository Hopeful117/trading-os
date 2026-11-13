package com.hope.trading.trading_core.executionauthorization;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ExecutionAuthorizationAuditRepository extends JpaRepository<ExecutionAuthorizationAuditEntity, UUID> { }
