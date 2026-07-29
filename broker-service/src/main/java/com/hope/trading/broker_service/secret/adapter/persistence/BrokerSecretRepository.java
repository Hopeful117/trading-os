package com.hope.trading.broker_service.secret.adapter.persistence;

import com.hope.trading.broker_service.secret.domain.SecretStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

interface BrokerSecretRepository extends JpaRepository<BrokerSecretEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BrokerSecretEntity> findByBrokerAccountIdAndStatus(UUID accountId, SecretStatus status);
    Optional<BrokerSecretEntity> findFirstByBrokerAccountIdOrderBySecretVersionDesc(UUID accountId);
    long countByBrokerAccountIdAndStatus(UUID accountId, SecretStatus status);
}
