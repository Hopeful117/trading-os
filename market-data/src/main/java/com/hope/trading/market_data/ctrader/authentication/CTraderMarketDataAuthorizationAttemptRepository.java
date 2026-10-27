package com.hope.trading.market_data.ctrader.authentication;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CTraderMarketDataAuthorizationAttemptRepository
        extends JpaRepository<CTraderMarketDataAuthorizationAttempt, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attempt from CTraderMarketDataAuthorizationAttempt attempt where attempt.stateHash = :stateHash")
    Optional<CTraderMarketDataAuthorizationAttempt> findByStateHashForUpdate(
            @Param("stateHash") String stateHash);
}
