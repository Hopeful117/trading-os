package com.hope.trading.trading_core.positionclose.infrastructure.persistence;

import com.hope.trading.trading_core.positionclose.domain.model.PositionCloseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaPositionCloseCommandRepository extends JpaRepository<PositionCloseCommandEntity, UUID> {
    Optional<PositionCloseCommandEntity> findByIdempotencyKey(String idempotencyKey);

    List<PositionCloseCommandEntity> findByAccountId(UUID accountId);

    @Query("SELECT e FROM PositionCloseCommandEntity e WHERE e.brokerAccountId = :brokerAccountId AND e.resolvedMutationScope = :scope AND e.status IN :statuses")
    List<PositionCloseCommandEntity> findActiveByBrokerAccountAndScope(UUID brokerAccountId, String scope, List<PositionCloseStatus> statuses);

    default List<PositionCloseCommandEntity> findActiveByBrokerAccountAndScope(UUID brokerAccountId, String resolvedMutationScope) {
        return findActiveByBrokerAccountAndScope(brokerAccountId, resolvedMutationScope, List.of(
                PositionCloseStatus.CREATED, PositionCloseStatus.SUBMITTED, PositionCloseStatus.ACKNOWLEDGED, PositionCloseStatus.UNKNOWN
        ));
    }
}