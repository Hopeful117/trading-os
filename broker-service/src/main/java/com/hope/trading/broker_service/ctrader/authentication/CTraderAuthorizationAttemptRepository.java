package com.hope.trading.broker_service.ctrader.authentication;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CTraderAuthorizationAttemptRepository extends JpaRepository<CTraderAuthorizationAttempt, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attempt from CTraderAuthorizationAttempt attempt where attempt.stateHash = :stateHash")
    Optional<CTraderAuthorizationAttempt> findByStateHashForUpdate(@Param("stateHash") String stateHash);

    Optional<CTraderAuthorizationAttempt> findByIdAndOwnerId(UUID id, UUID ownerId);
}
