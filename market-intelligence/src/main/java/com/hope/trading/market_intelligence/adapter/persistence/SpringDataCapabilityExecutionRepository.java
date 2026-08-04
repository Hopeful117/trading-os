package com.hope.trading.market_intelligence.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

interface SpringDataCapabilityExecutionRepository
        extends JpaRepository<JpaCapabilityExecutionEntity, UUID> {
    List<JpaCapabilityExecutionEntity> findByAnalysisExecutionIdOrderByCreatedAtAsc(UUID analysisExecutionId);
}
