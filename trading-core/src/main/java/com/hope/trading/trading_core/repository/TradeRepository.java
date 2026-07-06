package com.hope.trading.trading_core.repository;

import com.hope.trading.trading_core.model.Trade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TradeRepository extends JpaRepository<Trade, UUID> {
    List<Trade> findByAccount_AccountIdAndOpenedAtBetween(
            UUID accountId,
            Instant start,
            Instant end
    );

    List<Trade> findAllByAccount_AccountId(UUID accountId);

    int countByAccount_AccountIdAndClosedAtIsNotNull(UUID accountId);

    int countByAccount_AccountIdAndClosedAtIsNull(UUID accountId);
}
