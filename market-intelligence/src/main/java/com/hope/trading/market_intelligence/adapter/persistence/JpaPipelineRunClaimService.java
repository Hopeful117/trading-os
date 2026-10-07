package com.hope.trading.market_intelligence.adapter.persistence;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class JpaPipelineRunClaimService {
    private final JpaIntelligencePipelineRunRepository runs;

    public JpaPipelineRunClaimService(JpaIntelligencePipelineRunRepository runs) {
        this.runs = runs;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public JpaIntelligencePipelineRunEntity create(UUID analysisExecutionId,
                                                     String pipelineVersion,
                                                     Instant startedAt) {
        return runs.saveAndFlush(JpaIntelligencePipelineRunEntity.running(
                analysisExecutionId, pipelineVersion, startedAt));
    }
}
