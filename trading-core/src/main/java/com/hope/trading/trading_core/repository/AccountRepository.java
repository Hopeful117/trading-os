package com.hope.trading.trading_core.repository;

import com.hope.trading.trading_core.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
}
