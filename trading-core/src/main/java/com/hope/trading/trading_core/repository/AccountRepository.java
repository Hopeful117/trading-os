package com.hope.trading.trading_core.repository;

import com.hope.trading.trading_core.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account>findByUser_UserIdAndBroker(UUID uuid,String broker);
    boolean existsByUser_UserIdAndBroker(UUID uuid, String broker);
    List<Account>findAllByUser_UserId(UUID uuid);
}
