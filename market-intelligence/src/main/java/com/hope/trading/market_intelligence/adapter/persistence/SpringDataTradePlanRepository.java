package com.hope.trading.market_intelligence.adapter.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface SpringDataTradePlanRepository extends JpaRepository<JpaTradePlanEntity, JpaTradePlanId> {
    Optional<JpaTradePlanEntity> findTopByTradePlanIdOrderByVersionDesc(UUID tradePlanId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<JpaTradePlanEntity> findFirstByTradePlanIdOrderByVersionDesc(UUID tradePlanId);

    Optional<JpaTradePlanEntity> findFirstByTradePlanIdAndVersionGreaterThanOrderByVersionAsc(
            UUID tradePlanId, long version);

    List<JpaTradePlanEntity> findByTradePlanIdOrderByVersionAsc(UUID tradePlanId);
}
