package com.hope.trading.market_intelligence.application.research.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.domain.research.ResearchReport;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.hope.trading.market_intelligence.application.research.evaluation.ResearchEvaluation.DatasetKind.SYNTHETIC;
import static com.hope.trading.market_intelligence.application.research.evaluation.ResearchEvaluation.Outcome.FAIL;
import static org.assertj.core.api.Assertions.assertThat;

class ResearchEvaluationTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final UUID marketId = UUID.randomUUID();
    private final UUID evidenceId = UUID.randomUUID();

    @Test
    void publishesSixExplicitSyntheticScenarios() {
        assertThat(ResearchEvaluationFixtures.all()).hasSize(6)
                .allSatisfy(value -> assertThat(value.datasetKind()).isEqualTo(SYNTHETIC));
    }

    @Test
    void evaluatesEverySyntheticScenarioAgainstItsDeclaredExpectations() {
        var reports = ResearchEvaluationFixtures.reports(mapper);

        for (ResearchEvaluation.Scenario scenario : ResearchEvaluationFixtures.all()) {
            ResearchEvaluation.Result result = ResearchEvaluation.evaluate(scenario, reports.get(scenario.id()));
            if (scenario.expectsFollowUp()) {
                assertThat(result.passedMachineChecks()).as(scenario.id() + " -> " + result.criteria()).isFalse();
            } else {
                assertThat(result.passedMachineChecks()).as(scenario.id() + " -> " + result.criteria()).isTrue();
            }
        }
    }

    @Test
    void passesGroundedTrendReport() {
        ResearchEvaluation.Result result = ResearchEvaluation.evaluate(
                new ResearchEvaluation.Scenario("trend", SYNTHETIC, false, false, false, true, false),
                report("CURRENT", List.of(evidence("SUPPORTING")), List.of(), List.of(), List.of("breaks support"), null));

        assertThat(result.passedMachineChecks()).isTrue();
        assertThat(result.criteria()).allSatisfy(value -> assertThat(value.outcome()).isNotEqualTo(FAIL));
    }

    @Test
    void rejectsUnknownEvidenceInAiInterpretation() {
        JsonNode interpretation = mapper.createObjectNode().set("findings", mapper.createArrayNode().add(
                mapper.createObjectNode().put("findingId", "finding-1")
                        .put("description", "unsupported")
                        .put("type", "AI_FINDING")
                        .put("title", "supporting")
                        .set("evidenceReferences", mapper.createArrayNode().add(UUID.randomUUID().toString()))));
        ResearchEvaluation.Result result = ResearchEvaluation.evaluate(
                new ResearchEvaluation.Scenario("grounding", SYNTHETIC, false, false, false, false, false),
                report("CURRENT", List.of(evidence("SUPPORTING")), List.of(), List.of(), List.of(), interpretation));

        assertThat(result.passedMachineChecks()).isFalse();
        assertThat(result.criteria()).anyMatch(value -> value.criterion().equals("evidence-grounding")
                && value.outcome() == FAIL);
    }

    @Test
    void rejectsEmptyAiFindingsCollection() {
        JsonNode interpretation = mapper.createObjectNode().set("findings", mapper.createArrayNode());

        ResearchEvaluation.Result result = ResearchEvaluation.evaluate(
                new ResearchEvaluation.Scenario("empty-findings", SYNTHETIC, false, false, false, false, false),
                report("CURRENT", List.of(evidence("SUPPORTING")), List.of(), List.of(), List.of(), interpretation));

        assertThat(result.criteria()).filteredOn(value -> value.criterion().equals("evidence-grounding"))
                .extracting(ResearchEvaluation.CriterionResult::outcome)
                .containsExactly(FAIL);
    }

    @Test
    void rejectsNonArrayAiFindingsCollection() {
        JsonNode interpretation = mapper.createObjectNode().set("findings", mapper.createObjectNode());

        ResearchEvaluation.Result result = ResearchEvaluation.evaluate(
                new ResearchEvaluation.Scenario("object-findings", SYNTHETIC, false, false, false, false, false),
                report("CURRENT", List.of(evidence("SUPPORTING")), List.of(), List.of(), List.of(), interpretation));

        assertThat(result.criteria()).filteredOn(value -> value.criterion().equals("evidence-grounding"))
                .extracting(ResearchEvaluation.CriterionResult::outcome)
                .containsExactly(FAIL);
    }

    @Test
    void rejectsClaimsThatReferenceMissingEvidence() {
        JsonNode interpretation = mapper.createObjectNode().set("findings", mapper.createArrayNode().add(
                mapper.createObjectNode().set("evidenceReferences",
                        mapper.createArrayNode().add(evidenceId.toString()))));
        ResearchEvaluation.Result result = ResearchEvaluation.evaluate(
                new ResearchEvaluation.Scenario("missing-reference", SYNTHETIC, false, true, false, false, false),
                report("CURRENT", List.of(), List.of(), List.of(evidence("MISSING")), List.of(), interpretation));

        assertThat(result.criteria()).filteredOn(value -> value.criterion().equals("evidence-grounding"))
                .extracting(ResearchEvaluation.CriterionResult::outcome)
                .containsExactly(FAIL);
    }

    @Test
    void comparesDeterministicAndAssistedReportsWithoutAnOpaqueScore() {
        ResearchEvaluation.Scenario scenario = new ResearchEvaluation.Scenario(
                "comparison", SYNTHETIC, false, false, false, true, false);
        ResearchReport baseline = report("CURRENT", List.of(evidence("SUPPORTING")), List.of(), List.of(),
                List.of("breaks support"), null);
        JsonNode assistedInterpretation = mapper.createObjectNode().set("findings", mapper.createArrayNode().add(
                mapper.createObjectNode().put("findingId", "finding-1")
                        .put("description", "supported")
                        .put("type", "AI_FINDING")
                        .put("title", "supporting")
                        .set("evidenceReferences", mapper.createArrayNode().add(evidenceId.toString()))));

        ResearchEvaluation.Comparison comparison = ResearchEvaluation.compare(scenario, baseline,
                report("CURRENT", List.of(evidence("SUPPORTING")), List.of(), List.of(),
                        List.of("breaks support"), assistedInterpretation));

        assertThat(comparison.deterministicBaseline().passedMachineChecks()).isTrue();
        assertThat(comparison.assistedResult().passedMachineChecks()).isTrue();
        assertThat(comparison.assistedResult().criteria()).hasSize(9);
    }

    @Test
    void evaluatesMissingEvidenceAndAuthorizedFollowUp() {
        ResearchReport.AdaptiveCapabilityRecord followUp = new ResearchReport.AdaptiveCapabilityRecord(
                "market-structure-analysis", "1.1.0", marketId, "test uncertainty",
                "structure levels", "COMPLETE", UUID.randomUUID(), null);
        ResearchEvaluation.Result result = ResearchEvaluation.evaluate(
                new ResearchEvaluation.Scenario("adaptive", SYNTHETIC, false, true, false, true, true),
                report("CURRENT", List.of(), List.of(), List.of(evidence("MISSING")),
                        List.of("retest invalidates"), null, List.of(followUp)));

        assertThat(result.passedMachineChecks()).isFalse();
        assertThat(followUp.interpretationEffect()).isEqualTo("NOT_REEVALUATED");
        assertThat(result.criteria()).filteredOn(value -> value.criterion().equals("follow-up-effect"))
                .extracting(ResearchEvaluation.CriterionResult::outcome)
                .containsExactly(ResearchEvaluation.Outcome.INSUFFICIENT_EVIDENCE);
    }

    @Test
    void treatsLegacyNullInterpretationEffectAsNotReevaluated() {
        ResearchReport.AdaptiveCapabilityRecord followUp = new ResearchReport.AdaptiveCapabilityRecord(
                "market-structure-analysis", "1.1.0", marketId, "test uncertainty",
                "structure levels", "COMPLETE", UUID.randomUUID(), null, null);
        ResearchEvaluation.Result result = ResearchEvaluation.evaluate(
                new ResearchEvaluation.Scenario("legacy", SYNTHETIC, false, true, false, true, true),
                report("CURRENT", List.of(), List.of(), List.of(evidence("MISSING")),
                        List.of("retest invalidates"), null, List.of(followUp)));

        assertThat(result.criteria()).filteredOn(value -> value.criterion().equals("follow-up-effect"))
                .extracting(ResearchEvaluation.CriterionResult::outcome)
                .containsExactly(ResearchEvaluation.Outcome.INSUFFICIENT_EVIDENCE);
    }

    @Test
    void rejectsExecutedFollowUpWithoutRequestedAllowlistedRecord() {
        ResearchReport.AdaptiveCapabilityRecord followUp = new ResearchReport.AdaptiveCapabilityRecord(
                "arbitrary-capability", "9.9.9", marketId, "untrusted", "anything",
                "COMPLETE", UUID.randomUUID(), null, "REFINED");
        ResearchReport base = report("CURRENT", List.of(), List.of(), List.of(evidence("MISSING")),
                List.of("retest invalidates"), null, List.of(followUp));
        ResearchReport withoutRequest = new ResearchReport(base.schemaVersion(), base.objective(), base.depth(),
                base.generatedAt(), base.sourceCutoff(), base.hypotheses(), base.markets(), List.of(),
                base.executedCapabilities(), base.rejectedCapabilities(), base.limitations(),
                base.modelProvenance(), base.diagnostics(), base.conclusion());

        ResearchEvaluation.Result result = ResearchEvaluation.evaluate(
                new ResearchEvaluation.Scenario("unauthorized-follow-up", SYNTHETIC, false, true, false, true, true),
                withoutRequest);

        assertThat(result.criteria()).filteredOn(value -> value.criterion().equals("follow-up-governance"))
                .extracting(ResearchEvaluation.CriterionResult::outcome)
                .containsExactly(FAIL);
    }

    @Test
    void rejectsFollowUpPresentInBothExecutedAndRejectedCollections() {
        ResearchReport.AdaptiveCapabilityRecord followUp = new ResearchReport.AdaptiveCapabilityRecord(
                "market-structure-analysis", "1.1.0", marketId, "resolve uncertainty", "structure",
                "COMPLETE", UUID.randomUUID(), null, "REFINED");
        ResearchReport base = report("CURRENT", List.of(), List.of(), List.of(evidence("MISSING")),
                List.of("retest invalidates"), null, List.of(followUp));
        ResearchReport invalid = new ResearchReport(base.schemaVersion(), base.objective(), base.depth(),
                base.generatedAt(), base.sourceCutoff(), base.hypotheses(), base.markets(),
                base.requestedCapabilities(), base.executedCapabilities(), List.of(followUp),
                base.limitations(), base.modelProvenance(), base.diagnostics(), base.conclusion());

        ResearchEvaluation.Result result = ResearchEvaluation.evaluate(
                new ResearchEvaluation.Scenario("overlap", SYNTHETIC, false, true, false, true, true), invalid);

        assertThat(result.criteria()).filteredOn(value -> value.criterion().equals("follow-up-governance"))
                .extracting(ResearchEvaluation.CriterionResult::outcome)
                .containsExactly(FAIL);
    }

    @Test
    void rejectsIncompleteProvenance() {
        ResearchReport base = report("CURRENT", List.of(evidence("SUPPORTING")), List.of(), List.of(),
                List.of("breaks support"), null);
        ResearchReport.MarketResearchResult original = base.markets().get(0);
        ResearchReport.MarketResearchResult incomplete = new ResearchReport.MarketResearchResult(
                original.marketId(), original.provider(), original.instrument(), original.productType(),
                original.status(), original.thesisId(), original.thesisVersion(), original.deterministicAnalysis(),
                original.freshness(), mapper.createObjectNode().put("source", "fixture"),
                original.supportingEvidence(), original.contradictingEvidence(), original.missingEvidence(),
                original.invalidationConditions(), original.aiStatus(), original.aiInterpretation(), original.diagnostics());
        ResearchReport invalid = new ResearchReport(base.schemaVersion(), base.objective(), base.depth(),
                base.generatedAt(), base.sourceCutoff(), base.hypotheses(), List.of(incomplete),
                base.requestedCapabilities(), base.executedCapabilities(), base.rejectedCapabilities(),
                base.limitations(), base.modelProvenance(), base.diagnostics(), base.conclusion());

        ResearchEvaluation.Result result = ResearchEvaluation.evaluate(
                new ResearchEvaluation.Scenario("provenance", SYNTHETIC, false, false, false, true, false), invalid);

        assertThat(result.criteria()).filteredOn(value -> value.criterion().equals("provenance"))
                .extracting(ResearchEvaluation.CriterionResult::outcome)
                .containsExactly(FAIL);
    }

    @Test
    void identifiesStaleEvidenceWhenScenarioRequiresIt() {
        ResearchEvaluation.Result result = ResearchEvaluation.evaluate(
                new ResearchEvaluation.Scenario("stale", SYNTHETIC, false, false, true, false, false),
                report("STALE", List.of(evidence("SUPPORTING")), List.of(), List.of(), List.of(), null));

        assertThat(result.criteria()).filteredOn(value -> value.criterion().equals("freshness"))
                .extracting(ResearchEvaluation.CriterionResult::outcome)
                .containsExactly(ResearchEvaluation.Outcome.PASS);
    }

    private ResearchReport report(
            String freshness,
            List<JsonNode> supporting,
            List<JsonNode> contradicting,
            List<JsonNode> missing,
            List<String> invalidation,
            JsonNode interpretation) {
        return report(freshness, supporting, contradicting, missing, invalidation, interpretation, List.of());
    }

    private ResearchReport report(
            String freshness,
            List<JsonNode> supporting,
            List<JsonNode> contradicting,
            List<JsonNode> missing,
            List<String> invalidation,
            JsonNode interpretation,
            List<ResearchReport.AdaptiveCapabilityRecord> executed) {
        JsonNode provenance = mapper.createObjectNode()
                .put("source", "fixture")
                .put("capabilityId", "deterministic-market-research")
                .put("capabilityVersion", "1.0.0")
                .put("provider", "KRAKEN")
                .put("marketId", marketId.toString())
                .put("analysisExecutionId", UUID.randomUUID().toString())
                .put("evaluatedAt", "2026-11-05T12:00:00Z")
                .put("sourceCutoff", "2026-11-05T11:00:00Z")
                .put("inputFingerprint", "fixture-input");
        ResearchReport.MarketResearchResult market = new ResearchReport.MarketResearchResult(
                marketId, "KRAKEN", "BTC/USD", "SPOT", "COMPLETE", UUID.randomUUID(), 1L,
                mapper.createObjectNode(), freshness, provenance, supporting, contradicting, missing,
                invalidation, interpretation == null ? "DISABLED" : "COMPLETED", interpretation, List.of());
        List<ResearchReport.AdaptiveCapabilityRecord> requested = executed.stream()
                .map(value -> new ResearchReport.AdaptiveCapabilityRecord(value.capabilityId(), value.capabilityVersion(),
                        value.marketId(), value.reason(), value.expectedEvidence(), "PROPOSED", null, null))
                .toList();
        return new ResearchReport("research-report-v2", "fixture", "STANDARD", Instant.parse("2026-11-05T12:00:00Z"),
                Instant.parse("2026-11-05T11:00:00Z"), List.of(), List.of(market), requested, executed,
                List.of(), List.of(), "fixture", List.of(), "advisory");
    }

    private JsonNode evidence(String role) {
        return mapper.createObjectNode()
                .put("evidenceId", evidenceId.toString())
                .put("role", role)
                .put("source", "fixture")
                .put("reference", "fixture:" + role)
                .put("observedAt", "2026-11-05T11:00:00Z");
    }
}
