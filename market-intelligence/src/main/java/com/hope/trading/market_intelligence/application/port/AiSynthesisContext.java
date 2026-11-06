package com.hope.trading.market_intelligence.application.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AiSynthesisContext(
        String objective,
        UUID marketId,
        List<String> initialHypotheses,
        List<AiSynthesisFinding> initialFindings,
        RequestedAnalysis requestedAnalysis,
        FollowUpEvidence followUpEvidence
) {
    public AiSynthesisContext {
        initialHypotheses = List.copyOf(initialHypotheses);
        initialFindings = List.copyOf(initialFindings);
    }

    public record AiSynthesisFinding(
            String findingId,
            String title,
            String description,
            List<UUID> evidenceReferences
    ) {
        public AiSynthesisFinding {
            evidenceReferences = List.copyOf(evidenceReferences);
        }
    }

    public record FollowUpEvidence(
            UUID executionId,
            String capabilityId,
            String capabilityVersion,
            String status,
            Instant generatedAt,
            String sourceFingerprint,
            List<AiSynthesisFinding> findings,
            List<String> diagnostics
    ) {
        public FollowUpEvidence {
            findings = List.copyOf(findings);
            diagnostics = List.copyOf(diagnostics);
        }
    }
}
