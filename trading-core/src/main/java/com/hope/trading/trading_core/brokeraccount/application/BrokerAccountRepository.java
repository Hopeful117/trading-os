package com.hope.trading.trading_core.brokeraccount.application;

import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BrokerAccountRepository extends JpaRepository<BrokerAccount, UUID> {
    Optional<BrokerAccount> findByIdAndOwnerId(UUID id, UUID ownerId);
    List<BrokerAccount> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
}
