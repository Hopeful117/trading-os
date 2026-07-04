package com.hope.trading.trading_core.repository;

import com.hope.trading.trading_core.model.Trade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TradeRepository extends JpaRepository<Trade, UUID> {
}
