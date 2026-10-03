package com.hope.trading.news.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FinancialNewsItemRepository extends JpaRepository<FinancialNewsItemEntity, UUID> {
    @Query("select n from FinancialNewsItemEntity n "
            + "where n.publishedAt between :from and :to "
            + "and (:marketId is null or concat(',', n.marketIds, ',') like concat('%,', :marketId, ',%')) "
            + "and (:currency is null or lower(concat(',', n.currencies, ',')) like lower(concat('%,', :currency, ',%'))) "
            + "and (:impact is null or n.impact = :impact) "
            + "order by n.publishedAt desc")
    List<FinancialNewsItemEntity> findByPublishedAtBetweenOrderByPublishedAtDesc(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("marketId") String marketId,
            @Param("currency") String currency,
            @Param("impact") com.hope.trading.news.domain.ImpactLevel impact,
            Pageable pageable);
}
