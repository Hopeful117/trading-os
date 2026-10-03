package com.hope.trading.news.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EconomicEventRepository extends JpaRepository<EconomicEventEntity, UUID> {
    @Query("select e from EconomicEventEntity e "
            + "where e.scheduledAt between :from and :to "
            + "and (:marketId is null or concat(',', e.marketIds, ',') like concat('%,', :marketId, ',%')) "
            + "and (:currency is null or lower(concat(',', e.currencies, ',')) like lower(concat('%,', :currency, ',%'))) "
            + "and (:impact is null or e.impact = :impact) "
            + "order by e.scheduledAt asc")
    List<EconomicEventEntity> findByScheduledAtBetweenOrderByScheduledAtAsc(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("marketId") String marketId,
            @Param("currency") String currency,
            @Param("impact") com.hope.trading.news.domain.ImpactLevel impact,
            Pageable pageable);
}
