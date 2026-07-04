package com.hope.trading.trading_core.repository;

import com.hope.trading.trading_core.model.Trade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TradeRepository extends JpaRepository<Trade, UUID> {
    List<Trade> findByAccountIdAndOpenedAtBetween(
            UUID accountId,
            Instant start,
            Instant end
    );
    List<Trade> findAllByAccountId(UUID accountId);

    int countByAccountIdAndClosedAtIsNotNull(UUID accountId);

    int countByAccountIdAndClosedAtIsNull(UUID accountId);
}
