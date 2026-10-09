package com.hope.trading.trading_core.challenge.application;

import com.hope.trading.trading_core.challenge.domain.ChallengeInstance;
import com.hope.trading.trading_core.challenge.domain.ChallengeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface ChallengeInstanceRepository extends JpaRepository<ChallengeInstance, UUID> {
    boolean existsByAccountIdAndStatus(UUID accountId, ChallengeStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ChallengeInstance> findByAccountIdAndStatus(UUID accountId, ChallengeStatus status);

    Optional<ChallengeInstance> findTopByAccountIdOrderByStartedAtDesc(UUID accountId);

    long countByAccountIdAndStatus(UUID accountId, ChallengeStatus status);
}
