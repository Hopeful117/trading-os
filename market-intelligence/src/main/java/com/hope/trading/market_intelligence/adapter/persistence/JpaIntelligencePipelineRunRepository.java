package com.hope.trading.market_intelligence.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaIntelligencePipelineRunRepository
        extends JpaRepository<JpaIntelligencePipelineRunEntity, UUID> {
    Optional<JpaIntelligencePipelineRunEntity> findByAnalysisExecutionIdAndPipelineVersion(
            UUID analysisExecutionId, String pipelineVersion);
}
