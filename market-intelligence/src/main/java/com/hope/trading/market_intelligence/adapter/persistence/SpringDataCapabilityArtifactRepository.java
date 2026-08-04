package com.hope.trading.market_intelligence.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

interface SpringDataCapabilityArtifactRepository
        extends JpaRepository<JpaCapabilityArtifactEntity, UUID> {
    List<JpaCapabilityArtifactEntity> findByAnalysisExecutionIdAndArtifactTypeAndArtifactVersion(
            UUID analysisExecutionId, String artifactType, String artifactVersion);
    Optional<JpaCapabilityArtifactEntity>
    findByAnalysisExecutionIdAndArtifactTypeAndArtifactVersionAndParametersFingerprintAndInputFingerprint(
            UUID analysisExecutionId, String artifactType, String artifactVersion,
            String parametersFingerprint, String inputFingerprint);
}
