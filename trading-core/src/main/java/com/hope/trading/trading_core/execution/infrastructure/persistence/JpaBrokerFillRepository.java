package com.hope.trading.trading_core.execution.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaBrokerFillRepository extends JpaRepository<BrokerFillEntity, String> {
    List<BrokerFillEntity> findAllByBrokerOrderIdOrderByExecutedAtAsc(UUID brokerOrderId);

    void deleteAllByBrokerOrderId(UUID brokerOrderId);
}
