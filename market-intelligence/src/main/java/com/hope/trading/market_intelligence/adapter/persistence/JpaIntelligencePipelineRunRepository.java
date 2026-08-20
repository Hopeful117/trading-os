package com.hope.trading.market_intelligence.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaIntelligencePipelineRunRepository
        extends JpaRepository<JpaIntelligencePipelineRunEntity, UUID> {
    Optional<JpaIntelligencePipelineRunEntity> findByAnalysisExecutionIdAndPipelineVersion(
            UUID analysisExecutionId, String pipelineVersion);

    List<JpaIntelligencePipelineRunEntity> findByAnalysisExecutionIdInAndPipelineVersion(
            Collection<UUID> analysisExecutionIds,
            String pipelineVersion
    );
}
