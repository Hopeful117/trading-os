package com.hope.trading.trading_core.brokeraccount.application;

import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BrokerAccountRepository extends JpaRepository<BrokerAccount, UUID> {
    Optional<BrokerAccount> findByIdAndOwnerId(UUID id, UUID ownerId);
    Optional<BrokerAccount> findFirstByOwnerIdAndProviderRegistrationReferenceAndDisplayName(
            UUID ownerId, UUID providerRegistrationReference, String displayName);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from BrokerAccount account where account.id = :id and account.ownerId = :ownerId")
    Optional<BrokerAccount> findByIdAndOwnerIdForUpdate(@Param("id") UUID id, @Param("ownerId") UUID ownerId);
    List<BrokerAccount> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
}
