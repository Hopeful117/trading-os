package com.hope.trading.market_intelligence.adapter.persistence;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "intelligence_pipeline_runs",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_pipeline_analysis_version",
                columnNames = {"analysis_execution_id", "pipeline_version"}))
public class JpaIntelligencePipelineRunEntity {
    @Id
    @Column(name = "run_id", nullable = false)
    private UUID runId;
    @Column(name = "analysis_execution_id", nullable = false)
    private UUID analysisExecutionId;
    @Column(name = "pipeline_version", nullable = false, length = 80)
    private String pipelineVersion;
    @Column(name = "state", nullable = false, length = 40)
    private String state;
    @Column(name = "observation_id")
    private UUID observationId;
    @Column(name = "observation_version")
    private Long observationVersion;
    @Column(name = "opportunity_id")
    private UUID opportunityId;
    @Column(name = "opportunity_version")
    private Long opportunityVersion;
    @Column(name = "failure_code", length = 80)
    private String failureCode;
    @Column(name = "failure_message", length = 500)
    private String failureMessage;
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
    @Column(name = "completed_at")
    private Instant completedAt;

    protected JpaIntelligencePipelineRunEntity() {
    }

    public static JpaIntelligencePipelineRunEntity running(
            UUID analysisExecutionId, String pipelineVersion, Instant at) {
        JpaIntelligencePipelineRunEntity entity = new JpaIntelligencePipelineRunEntity();
        entity.runId = UUID.randomUUID();
        entity.analysisExecutionId = analysisExecutionId;
        entity.pipelineVersion = pipelineVersion;
        entity.state = "RUNNING";
        entity.startedAt = at;
        return entity;
    }

    public void complete(UUID observationId, long observationVersion,
                         UUID opportunityId, long opportunityVersion, Instant at) {
        this.state = "COMPLETED";
        this.observationId = observationId;
        this.observationVersion = observationVersion;
        this.opportunityId = opportunityId;
        this.opportunityVersion = opportunityVersion;
        this.completedAt = at;
    }

    public void noSignal(String message, Instant at) {
        this.state = "COMPLETED_NO_SIGNAL";
        this.failureCode = "NO_SIGNAL";
        this.failureMessage = message;
        this.completedAt = at;
    }

    public void fail(String stage, String message, Instant at) {
        this.state = "FAILED_" + stage;
        this.failureCode = "PIPELINE_" + stage + "_FAILED";
        this.failureMessage = message;
        this.completedAt = at;
    }

    public UUID analysisExecutionId() { return analysisExecutionId; }
    public String state() { return state; }
    public UUID opportunityId() { return opportunityId; }
    public Long opportunityVersion() { return opportunityVersion; }
}
