package com.hope.trading.market_intelligence.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataMarketThesisRepository extends JpaRepository<JpaMarketThesisEntity, UUID> {
    Optional<JpaMarketThesisEntity> findByThesisIdAndVersion(UUID thesisId, long version);
    Optional<JpaMarketThesisEntity> findByInputFingerprint(String inputFingerprint);
    List<JpaMarketThesisEntity> findByMarketIdOrderByEvaluatedAtDesc(UUID marketId);
    @Query("""
            select e from JpaMarketThesisEntity e
            where e.marketId = :marketId
              and lower(e.instrument) = lower(:instrument)
              and e.scenarioDirection = :direction
              and e.validFrom <= :at
              and (e.validUntil is null or e.validUntil > :at)
              and e.thesisId not in (
                    select superseded.supersedes
                    from JpaMarketThesisEntity superseded
                    where superseded.supersedes is not null
              )
            order by e.evaluatedAt desc
            """)
    List<JpaMarketThesisEntity> findLatestValid(
            @Param("marketId") UUID marketId,
            @Param("instrument") String instrument,
            @Param("direction") String direction,
            @Param("at") java.time.Instant at,
            org.springframework.data.domain.Pageable pageable);
}
