package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.application.port.AnalysisPipelineRunView;
import com.hope.trading.market_intelligence.application.port.AnalysisPipelineRunViewRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public class JpaAnalysisPipelineRunViewRepository implements AnalysisPipelineRunViewRepository {
    private final JpaIntelligencePipelineRunRepository repository;

    public JpaAnalysisPipelineRunViewRepository(JpaIntelligencePipelineRunRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnalysisPipelineRunView> findByAnalysisExecutionIdsAndPipelineVersion(
            Collection<UUID> analysisExecutionIds,
            String pipelineVersion
    ) {
        if (analysisExecutionIds.isEmpty()) {
            return List.of();
        }
        return repository.findByAnalysisExecutionIdInAndPipelineVersion(
                analysisExecutionIds,
                pipelineVersion
        ).stream().map(this::toView).toList();
    }

    private AnalysisPipelineRunView toView(JpaIntelligencePipelineRunEntity entity) {
        return new AnalysisPipelineRunView(
                entity.analysisExecutionId(),
                entity.pipelineVersion(),
                entity.state(),
                entity.opportunityId(),
                entity.opportunityVersion(),
                entity.failureCode(),
                entity.failureMessage(),
                entity.completedAt()
        );
    }
}
