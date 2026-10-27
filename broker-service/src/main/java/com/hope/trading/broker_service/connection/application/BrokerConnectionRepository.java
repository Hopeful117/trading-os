package com.hope.trading.broker_service.connection.application;

import com.hope.trading.broker_service.connection.domain.BrokerConnection;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface BrokerConnectionRepository extends JpaRepository<BrokerConnection, UUID> {
    Optional<BrokerConnection> findByBrokerAccountId(UUID brokerAccountId);
    Optional<BrokerConnection> findByBrokerAccountIdAndOwnerId(UUID brokerAccountId, UUID ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select connection from BrokerConnection connection where connection.brokerAccountId = :accountId and connection.ownerId = :ownerId")
    Optional<BrokerConnection> findByBrokerAccountIdAndOwnerIdForUpdate(@Param("accountId") UUID accountId,
                                                                          @Param("ownerId") UUID ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select connection from BrokerConnection connection where connection.brokerAccountId = :accountId")
    Optional<BrokerConnection> findByBrokerAccountIdForUpdate(@Param("accountId") UUID accountId);
}
