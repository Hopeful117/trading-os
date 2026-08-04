package com.hope.trading.market_intelligence.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

interface SpringDataAnalysisExecutionRepository
        extends JpaRepository<JpaAnalysisExecutionEntity, UUID> {
    Optional<JpaAnalysisExecutionEntity> findByIdempotencyKey(String idempotencyKey);
}
