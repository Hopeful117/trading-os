package com.hope.trading.trading_core.challenge.application;

import com.hope.trading.trading_core.challenge.domain.ChallengeInstance;
import com.hope.trading.trading_core.challenge.domain.ChallengeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ChallengeInstanceRepository extends JpaRepository<ChallengeInstance, UUID> {
    boolean existsByAccountIdAndStatus(UUID accountId, ChallengeStatus status);

    long countByAccountIdAndStatus(UUID accountId, ChallengeStatus status);
}
