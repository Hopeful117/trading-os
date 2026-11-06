package com.hope.trading.market_intelligence.application.research.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.hope.trading.market_intelligence.domain.research.ResearchReport;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Deterministic report checks; usefulness remains a separate human review. */
public final class ResearchEvaluation {
    private ResearchEvaluation() {
    }

    public enum DatasetKind { SYNTHETIC, REAL_REPLAY }

    public enum Outcome { PASS, FAIL, INSUFFICIENT_EVIDENCE }

    public record Scenario(
            String id,
            DatasetKind datasetKind,
            boolean expectsContradictions,
            boolean expectsMissingEvidence,
            boolean expectsStaleEvidence,
            boolean expectsInvalidation,
            boolean expectsFollowUp
    ) {
    }

    public record CriterionResult(String criterion, Outcome outcome, List<String> diagnostics) {
        public CriterionResult {
            diagnostics = List.copyOf(diagnostics);
        }
    }

    public record Result(String scenarioId, DatasetKind datasetKind, List<CriterionResult> criteria) {
        public Result {
            criteria = List.copyOf(criteria);
        }

        public boolean passedMachineChecks() {
            return criteria.stream().allMatch(value -> value.outcome() == Outcome.PASS);
        }
    }

    public record Comparison(Result deterministicBaseline, Result assistedResult) {
    }

    public static Result evaluate(Scenario scenario, ResearchReport report) {
        List<CriterionResult> criteria = new ArrayList<>();
        criteria.add(evidenceGrounding(report));
        criteria.add(provenance(report));
        criteria.add(freshness(scenario, report));
        criteria.add(contradictions(scenario, report));
        criteria.add(missingEvidence(scenario, report));
        criteria.add(invalidation(scenario, report));
        criteria.add(followUp(scenario, report));
        criteria.add(followUpEffect(scenario, report));
        criteria.add(consistency(report));
        return new Result(scenario.id(), scenario.datasetKind(), criteria);
    }

    public static Comparison compare(Scenario scenario, ResearchReport deterministicBaseline,
                                     ResearchReport assistedResult) {
        return new Comparison(evaluate(scenario, deterministicBaseline), evaluate(scenario, assistedResult));
    }

    private static CriterionResult evidenceGrounding(ResearchReport report) {
        List<String> diagnostics = new ArrayList<>();
        for (ResearchReport.MarketResearchResult market : report.markets()) {
            Set<UUID> allEvidenceIds = new HashSet<>();
            Set<UUID> claimEvidenceIds = new HashSet<>();
            for (JsonNode evidence : allEvidence(market)) {
                JsonNode id = evidence.get("evidenceId");
                UUID parsed = id != null && id.isTextual() ? parseUuid(id.asText()) : null;
                if (parsed == null || !allEvidenceIds.add(parsed)) {
                    diagnostics.add("evidence item has no valid evidenceId");
                }
                if (isClaimEvidence(evidence) && parsed != null) claimEvidenceIds.add(parsed);
                if (missingText(evidence, "source") || missingText(evidence, "reference")) {
                    diagnostics.add("evidence item is missing source or reference");
                }
            }
            collectAiReferences(market.aiInterpretation(), claimEvidenceIds, diagnostics);
            Set<UUID> authorizedEvidenceIds = new HashSet<>(claimEvidenceIds);
            collectEvidenceReferences(market.followUpEvidence(), authorizedEvidenceIds);
            collectAiReferences(market.finalAiInterpretation(), authorizedEvidenceIds, diagnostics);
            Set<UUID> followUpEvidenceIds = followUpEvidenceIds(market.followUpEvidence());
            for (ResearchReport.HypothesisReconciliation reconciliation : market.reconciliations()) {
                if (reconciliation.evidenceReferences().stream().anyMatch(value -> !authorizedEvidenceIds.contains(value))) {
                    diagnostics.add("hypothesis reconciliation references unknown evidence");
                }
                if (!List.of("UNCHANGED", "INCONCLUSIVE").contains(reconciliation.effect())
                        && reconciliation.evidenceReferences().isEmpty()) {
                    diagnostics.add("hypothesis revision has no supporting evidence reference");
                }
                if (!List.of("UNCHANGED", "INCONCLUSIVE").contains(reconciliation.effect())
                        && java.util.Collections.disjoint(reconciliation.evidenceReferences(), followUpEvidenceIds)) {
                    diagnostics.add("hypothesis revision does not reference follow-up evidence");
                }
            }
        }
        return result("evidence-grounding", diagnostics);
    }

    private static CriterionResult provenance(ResearchReport report) {
        List<String> diagnostics = new ArrayList<>();
        for (ResearchReport.MarketResearchResult market : report.markets()) {
            if (market.provenance() == null || !market.provenance().isObject()) {
                diagnostics.add("market is missing provenance");
                continue;
            }
            for (String field : List.of("marketId", "source", "provider", "capabilityId",
                    "capabilityVersion", "analysisExecutionId", "inputFingerprint",
                    "evaluatedAt", "sourceCutoff")) {
                if (missingText(market.provenance(), field)) diagnostics.add("provenance missing " + field);
            }
        }
        return result("provenance", diagnostics);
    }

    private static CriterionResult freshness(Scenario scenario, ResearchReport report) {
        boolean stale = report.markets().stream().anyMatch(value -> "STALE".equals(value.freshness()));
        if (scenario.expectsStaleEvidence() && !stale) {
            return new CriterionResult("freshness", Outcome.FAIL, List.of("expected stale evidence"));
        }
        if (!scenario.expectsStaleEvidence() && stale) {
            return new CriterionResult("freshness", Outcome.FAIL, List.of("unexpected stale evidence"));
        }
        return result("freshness", List.of());
    }

    private static CriterionResult contradictions(Scenario scenario, ResearchReport report) {
        boolean present = report.markets().stream().anyMatch(value -> !value.contradictingEvidence().isEmpty());
        return expected("contradictions", scenario.expectsContradictions(), present,
                "contradictory evidence was not preserved");
    }

    private static CriterionResult missingEvidence(Scenario scenario, ResearchReport report) {
        boolean present = report.markets().stream().anyMatch(value -> !value.missingEvidence().isEmpty());
        return expected("missing-evidence", scenario.expectsMissingEvidence(), present,
                "missing evidence was not preserved");
    }

    private static CriterionResult invalidation(Scenario scenario, ResearchReport report) {
        boolean present = report.markets().stream().anyMatch(value -> !value.invalidationConditions().isEmpty());
        return expected("invalidation", scenario.expectsInvalidation(), present,
                "invalidation conditions were not preserved");
    }

    private static CriterionResult followUp(Scenario scenario, ResearchReport report) {
        Set<UUID> marketIds = report.markets().stream().map(ResearchReport.MarketResearchResult::marketId).collect(java.util.stream.Collectors.toSet());
        Map<String, String> allowlist = Map.of(
                "market-structure-analysis", "1.1.0",
                "ohlc-momentum-analysis", "1.0.0");
        boolean present = !report.executedCapabilities().isEmpty()
                && report.executedCapabilities().size() <= 1
                && report.executedCapabilities().stream().allMatch(value -> {
                    boolean requested = report.requestedCapabilities().stream().anyMatch(request ->
                            java.util.Objects.equals(request.capabilityId(), value.capabilityId())
                                    && java.util.Objects.equals(request.capabilityVersion(), value.capabilityVersion())
                                    && java.util.Objects.equals(request.marketId(), value.marketId()));
                    boolean rejected = report.rejectedCapabilities().stream().anyMatch(rejection ->
                            java.util.Objects.equals(rejection.capabilityId(), value.capabilityId())
                                    && java.util.Objects.equals(rejection.capabilityVersion(), value.capabilityVersion())
                                    && java.util.Objects.equals(rejection.marketId(), value.marketId()));
                    return requested
                            && !rejected
                            && java.util.Objects.equals(allowlist.get(value.capabilityId()), value.capabilityVersion())
                            && marketIds.contains(value.marketId())
                            && value.executionId() != null
                            && List.of("COMPLETE", "PARTIAL").contains(value.status())
                            && !isBlank(value.reason())
                            && !isBlank(value.expectedEvidence());
                });
        if (scenario.expectsFollowUp() && !present) {
            return new CriterionResult("follow-up-governance", Outcome.FAIL,
                    List.of("expected an executed authorized follow-up"));
        }
        if (!scenario.expectsFollowUp() && present) {
            return new CriterionResult("follow-up-governance", Outcome.FAIL,
                    List.of("unexpected follow-up execution"));
        }
        return result("follow-up-governance", List.of());
    }

    private static CriterionResult consistency(ResearchReport report) {
        List<String> diagnostics = new ArrayList<>();
        for (ResearchReport.MarketResearchResult market : report.markets()) {
            if ("COMPLETE".equals(market.status()) && market.deterministicAnalysis() == null) {
                diagnostics.add("complete market has no deterministic analysis");
            }
        }
        return result("consistency", diagnostics);
    }

    private static CriterionResult followUpEffect(Scenario scenario, ResearchReport report) {
        if (!scenario.expectsFollowUp()) return result("follow-up-effect", List.of());
        Set<String> validEffects = Set.of("CONFIRMED", "STRENGTHENED", "WEAKENED", "INVALIDATED",
                "UNCHANGED", "INCONCLUSIVE", "SUPPORTED_REVISION", "NO_MATERIAL_CHANGE");
        boolean invalid = report.executedCapabilities().stream()
                .anyMatch(value -> value.interpretationEffect() == null
                        || !validEffects.contains(value.interpretationEffect()));
        boolean notReevaluated = report.executedCapabilities().stream()
                .anyMatch(value -> value.interpretationEffect() == null
                        || "NOT_REEVALUATED".equals(value.interpretationEffect())
                        || "FINAL_SYNTHESIS_FAILED".equals(value.interpretationEffect()));
        boolean finalSynthesisMissing = report.executedCapabilities().stream()
                .anyMatch(value -> report.markets().stream().anyMatch(market ->
                        java.util.Objects.equals(market.marketId(), value.marketId())
                                && (market.finalAiInterpretation() == null || market.reconciliations().isEmpty())));
        return invalid || notReevaluated || finalSynthesisMissing
                ? new CriterionResult("follow-up-effect", Outcome.INSUFFICIENT_EVIDENCE,
                List.of("follow-up evidence was executed but final interpretation was unavailable"))
                : result("follow-up-effect", List.of());
    }

    private static CriterionResult expected(String criterion, boolean expected, boolean present, String message) {
        if (expected == present) return result(criterion, List.of());
        return new CriterionResult(criterion, Outcome.FAIL, List.of(message));
    }

    private static CriterionResult result(String criterion, List<String> diagnostics) {
        return new CriterionResult(criterion, diagnostics.isEmpty() ? Outcome.PASS : Outcome.FAIL, diagnostics);
    }

    private static List<JsonNode> allEvidence(ResearchReport.MarketResearchResult market) {
        List<JsonNode> evidence = new ArrayList<>();
        evidence.addAll(market.supportingEvidence());
        evidence.addAll(market.contradictingEvidence());
        evidence.addAll(market.missingEvidence());
        return evidence;
    }

    private static void collectEvidenceReferences(JsonNode node, Set<UUID> target) {
        if (node == null || node.isNull()) return;
        if (node.isObject()) {
            JsonNode references = node.get("evidenceReferences");
            if (references != null && references.isArray()) {
                references.forEach(value -> {
                    if (value.isTextual()) parseUuid(value.asText(), target);
                });
            }
            node.elements().forEachRemaining(value -> collectEvidenceReferences(value, target));
        } else if (node.isArray()) {
            node.forEach(value -> collectEvidenceReferences(value, target));
        }
    }

    private static Set<UUID> followUpEvidenceIds(JsonNode node) {
        Set<UUID> ids = new HashSet<>();
        if (node == null || !node.isObject()) return ids;
        JsonNode findings = node.get("findings");
        if (findings == null || !findings.isArray()) return ids;
        findings.forEach(finding -> {
            JsonNode id = finding.get("findingId");
            if (id != null && id.isTextual()) {
                UUID parsed = parseUuid(id.asText());
                if (parsed != null) ids.add(parsed);
            }
        });
        return ids;
    }

    private static void collectAiReferences(JsonNode node, Set<UUID> evidenceIds, List<String> diagnostics) {
        collectAiReferences(node, evidenceIds, diagnostics, true);
    }

    private static void collectAiReferences(JsonNode node, Set<UUID> evidenceIds,
                                            List<String> diagnostics, boolean root) {
        if (node == null || node.isNull()) return;
        if (node.isObject()) {
            if (root) {
                JsonNode findings = node.get("findings");
                if (findings == null || !findings.isArray() || findings.isEmpty()) {
                    diagnostics.add("AI interpretation has no non-empty findings collection");
                }
            }
            node.fields().forEachRemaining(entry -> {
                if (entry.getKey().equals("findings") && entry.getValue().isArray()) {
                    entry.getValue().forEach(finding -> {
                        if (missingText(finding, "findingId") || missingText(finding, "description")
                                || missingText(finding, "type")) {
                            diagnostics.add("AI finding has incomplete structure");
                        }
                        String title = finding.path("title").asText();
                        if ("supporting".equalsIgnoreCase(title) || "contradiction".equalsIgnoreCase(title)) {
                            JsonNode references = finding.get("evidenceReferences");
                            if (references == null || !references.isArray() || references.isEmpty()) {
                                diagnostics.add("AI claim has no supporting evidence reference");
                            }
                        }
                    });
                }
                if (entry.getKey().equals("evidenceReferences") || entry.getKey().equals("evidenceRefs")) {
                    if (entry.getValue().isArray()) {
                        entry.getValue().forEach(value -> {
                            if (!value.isTextual() || !evidenceIds.contains(parseUuid(value.asText()))) {
                                diagnostics.add("AI interpretation references unknown evidence");
                            }
                        });
                    }
                }
                collectAiReferences(entry.getValue(), evidenceIds, diagnostics, false);
            });
        } else if (node.isArray()) {
            node.forEach(value -> collectAiReferences(value, evidenceIds, diagnostics, false));
        }
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean parseUuid(String value, Set<UUID> target) {
        UUID parsed = parseUuid(value);
        return parsed != null && target.add(parsed);
    }

    private static boolean missingText(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || !value.isTextual() || value.asText().isBlank();
    }

    private static boolean isClaimEvidence(JsonNode evidence) {
        return !"MISSING".equalsIgnoreCase(evidence.path("role").asText());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
