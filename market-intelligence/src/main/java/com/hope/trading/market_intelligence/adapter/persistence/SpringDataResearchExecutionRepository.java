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
    @Query("update JpaResearchExecutionEntity value set value.status = 'RUNNING', value.updatedAt = :now "
            + "where value.researchId = :researchId and value.status = 'REQUESTED'")
    int claimRequested(@Param("researchId") UUID researchId, @Param("now") java.time.Instant now);
}
