package com.hope.trading.broker_service.connection.application;

import com.hope.trading.broker_service.connection.domain.BrokerRevocationNotification;
import com.hope.trading.broker_service.connection.domain.BrokerRevocationNotificationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BrokerRevocationNotificationRepository
        extends JpaRepository<BrokerRevocationNotification, UUID> {
    Optional<BrokerRevocationNotification> findByBrokerAccountId(UUID brokerAccountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select notification from BrokerRevocationNotification notification where notification.id = :id")
    Optional<BrokerRevocationNotification> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select notification.id
            from BrokerRevocationNotification notification
            where notification.deliveryStatus in :statuses
              and notification.retryExhausted = false
              and notification.attemptCount < :maxAttempts
              and notification.nextAttemptAt <= :now
              and (notification.leaseUntil is null or notification.leaseUntil <= :now)
            order by notification.nextAttemptAt, notification.createdAt
            """)
    List<UUID> findDueIds(@Param("statuses") List<BrokerRevocationNotificationStatus> statuses,
                          @Param("now") Instant now, @Param("maxAttempts") int maxAttempts,
                          Pageable pageable);
}
