package com.hope.trading.trading_core.challenge.application;

import com.hope.trading.trading_core.challenge.domain.ChallengeInstance;
import com.hope.trading.trading_core.challenge.domain.ChallengeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChallengeInstanceRepository extends JpaRepository<ChallengeInstance, UUID> {
    boolean existsByAccountIdAndStatus(UUID accountId, ChallengeStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ChallengeInstance> findByAccountIdAndStatus(UUID accountId, ChallengeStatus status);

    @Query("""
            select c from ChallengeInstance c
            where c.accountId = :accountId
              and c.status = :status
            """)
    Optional<ChallengeInstance> findForReadiness(@Param("accountId") UUID accountId,
                                                 @Param("status") ChallengeStatus status);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ChallengeInstance> findById(UUID id);

    @Query("""
            select c from ChallengeInstance c
            where c.status = :status
            order by c.startedAt asc, c.id asc
            """)
    List<ChallengeInstance> findFirstPageByStatus(@Param("status") ChallengeStatus status,
                                                  Pageable pageable);

    @Query("""
            select c from ChallengeInstance c
            where c.status = :status
              and (c.startedAt > :afterStartedAt
                   or (c.startedAt = :afterStartedAt and c.id > :afterId))
            order by c.startedAt asc, c.id asc
            """)
    List<ChallengeInstance> findNextPageByStatus(@Param("status") ChallengeStatus status,
                                                 @Param("afterStartedAt") Instant afterStartedAt,
                                                 @Param("afterId") UUID afterId,
                                                 Pageable pageable);

    Optional<ChallengeInstance> findTopByAccountIdOrderByStartedAtDesc(UUID accountId);

    long countByAccountIdAndStatus(UUID accountId, ChallengeStatus status);
}
