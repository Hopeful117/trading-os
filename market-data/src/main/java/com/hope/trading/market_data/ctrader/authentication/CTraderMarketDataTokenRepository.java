package com.hope.trading.market_data.ctrader.authentication;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

interface CTraderMarketDataTokenRepository extends JpaRepository<CTraderMarketDataTokenEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CTraderMarketDataTokenEntity> findFirstByStatus(CTraderTokenStatus status);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CTraderMarketDataTokenEntity> findById(UUID id);
}
