package com.hope.trading.trading_core.execution.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface JpaBrokerOrderRepository extends JpaRepository<BrokerOrderEntity, UUID> {
    Optional<BrokerOrderEntity> findByIntentId(UUID intentId);
}
