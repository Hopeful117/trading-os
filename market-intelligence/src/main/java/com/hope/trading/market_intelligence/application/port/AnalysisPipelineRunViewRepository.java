package com.hope.trading.market_intelligence.application.port;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AnalysisPipelineRunViewRepository {
    List<AnalysisPipelineRunView> findByAnalysisExecutionIdsAndPipelineVersion(
            Collection<UUID> analysisExecutionIds,
            String pipelineVersion
    );
}
