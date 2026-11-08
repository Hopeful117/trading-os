package com.hope.trading.market_intelligence.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

interface SpringDataResearchExecutionRepository extends JpaRepository<JpaResearchExecutionEntity, UUID> {
    Optional<JpaResearchExecutionEntity> findByActorIdAndIdempotencyKey(UUID actorId, String idempotencyKey);

    List<JpaResearchExecutionEntity> findByStatus(String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select value from JpaResearchExecutionEntity value where value.researchId = :researchId")
    Optional<JpaResearchExecutionEntity> findForUpdate(@Param("researchId") UUID researchId);

    @Modifying
    @Query("update JpaResearchExecutionEntity value set value.status = 'RUNNING', value.updatedAt = :now, "
            + "value.attemptCount = value.attemptCount + 1, value.currentStage = 'DETERMINISTIC_ANALYSIS', "
            + "value.lastProgressAt = :now "
            + "where value.researchId = :researchId and value.status = 'REQUESTED'")
    int claimRequested(@Param("researchId") UUID researchId, @Param("now") java.time.Instant now);

    @Modifying
    @Query("update JpaResearchExecutionEntity value set value.status = 'REQUESTED', value.updatedAt = :now, "
            + "value.currentStage = 'REQUEUED', value.lastProgressAt = :now "
            + "where value.researchId = :researchId and value.status = 'RUNNING' "
            + "and value.attemptCount = :attemptCount and value.updatedAt = :observedUpdatedAt "
            + "and value.updatedAt < :staleBefore")
    int requeueStale(@Param("researchId") UUID researchId, @Param("attemptCount") int attemptCount,
                     @Param("observedUpdatedAt") java.time.Instant observedUpdatedAt,
                     @Param("staleBefore") java.time.Instant staleBefore,
                     @Param("now") java.time.Instant now);
}
