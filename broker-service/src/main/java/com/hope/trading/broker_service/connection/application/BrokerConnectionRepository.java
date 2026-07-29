package com.hope.trading.broker_service.connection.application;

import com.hope.trading.broker_service.connection.domain.BrokerConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BrokerConnectionRepository extends JpaRepository<BrokerConnection, UUID> {
    Optional<BrokerConnection> findByBrokerAccountId(UUID brokerAccountId);
    Optional<BrokerConnection> findByBrokerAccountIdAndOwnerId(UUID brokerAccountId, UUID ownerId);
}
