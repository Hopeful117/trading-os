package com.hope.trading.trading_core.execution.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface JpaExecutionEventRepository extends JpaRepository<ExecutionEventEntity,UUID>{}
