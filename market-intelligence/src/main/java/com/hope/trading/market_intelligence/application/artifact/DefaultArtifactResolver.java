package com.hope.trading.market_intelligence.application.artifact;

import com.hope.trading.market_intelligence.application.port.ArtifactStore;
import com.hope.trading.market_intelligence.domain.artifact.*;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecution;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Makes compatibility and reuse decisions. The store only retrieves data;
 * it never decides whether an artifact is safe to reuse.
 */
@Service
public class DefaultArtifactResolver implements ArtifactResolver {
    private final ArtifactStore store;
    private final FreshnessEvaluator freshnessEvaluator;
    private final Clock clock;

    public DefaultArtifactResolver(
            ArtifactStore store,
            FreshnessEvaluator freshnessEvaluator,
            Clock clock
    ) {
        this.store = store;
        this.freshnessEvaluator = freshnessEvaluator;
        this.clock = clock;
    }

    @Override
    public ArtifactResolution resolve(
            ArtifactRequirement requirement,
            AnalysisExecution execution
    ) {
        Instant now = clock.instant();
        if (execution.isExpiredAt(now) || execution.status().isTerminal()) {
            return absent(
                    ReuseDecision.REJECT, false,
                    "Execution is expired or terminal"
            );
        }
        if (requirement.recalculationPolicy() == RecalculationPolicy.FORCED) {
            return absent(
                    ReuseDecision.RECALCULATE, true,
                    "Requirement forces recalculation"
            );
        }

        return store.find(requirement.key())
                .map(artifact -> resolveStored(requirement, execution, artifact, now))
                .orElseGet(() -> missing(requirement));
    }

    private ArtifactResolution resolveStored(
            ArtifactRequirement requirement,
            AnalysisExecution execution,
            StoredArtifact artifact,
            Instant now
    ) {
        if (!artifact.key().scope().isCompatibleWith(requirement.key().scope())) {
            return absent(ReuseDecision.REJECT, false, "Artifact scope is incompatible");
        }
        if (!requirement.accepts(artifact.quality())) {
            return recalculateOrReject(requirement, "Artifact quality is insufficient");
        }
        FreshnessAssessment assessment = freshnessEvaluator.assess(
                artifact.freshness(),
                requirement.freshnessPolicy(),
                execution.provenance().mode(),
                now
        );
        if (!assessment.reusable()) {
            return recalculateOrReject(requirement, assessment.reason(), assessment);
        }

        ArtifactProvenance provenance =
                artifact.provenance().reusedBy(execution.executionId());
        StoredArtifact traced = new StoredArtifact(
                artifact.key(), artifact.content(), artifact.freshness(),
                provenance, artifact.quality()
        );
        store.save(traced);
        boolean warning = assessment.warning();
        return new ArtifactResolution(
                warning ? ReuseDecision.REUSE_WITH_WARNING : ReuseDecision.REUSE,
                traced,
                assessment,
                provenance,
                true,
                false,
                warning,
                assessment.reason()
        );
    }

    private ArtifactResolution missing(ArtifactRequirement requirement) {
        if (requirement.recalculationPolicy() == RecalculationPolicy.FORBIDDEN) {
            return absent(ReuseDecision.REJECT, false, "Artifact is missing and recalculation is forbidden");
        }
        return absent(ReuseDecision.RECALCULATE, true, "No compatible artifact exists");
    }

    private ArtifactResolution recalculateOrReject(
            ArtifactRequirement requirement,
            String reason
    ) {
        return recalculateOrReject(requirement, reason, null);
    }

    private ArtifactResolution recalculateOrReject(
            ArtifactRequirement requirement,
            String reason,
            FreshnessAssessment assessment
    ) {
        boolean recalculate =
                requirement.recalculationPolicy() != RecalculationPolicy.FORBIDDEN;
        return new ArtifactResolution(
                recalculate ? ReuseDecision.RECALCULATE : ReuseDecision.REJECT,
                null,
                assessment,
                null,
                false,
                recalculate,
                false,
                reason
        );
    }

    private ArtifactResolution absent(
            ReuseDecision decision,
            boolean recalculation,
            String reason
    ) {
        return new ArtifactResolution(
                decision, null, null, null, false, recalculation, false, reason
        );
    }
}
