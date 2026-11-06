package com.hope.trading.market_intelligence.application.research.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.domain.research.ResearchReport;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.hope.trading.market_intelligence.application.research.evaluation.ResearchEvaluation.DatasetKind.SYNTHETIC;

/** Scenario metadata only; values are synthetic and never presented as market facts. */
public final class ResearchEvaluationFixtures {
    private static final UUID BTC = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ETH = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID FOLLOW_UP = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private ResearchEvaluationFixtures() {
    }

    public static List<ResearchEvaluation.Scenario> all() {
        return List.of(
                new ResearchEvaluation.Scenario("A-trend-confirmation", SYNTHETIC, false, false, false, true, false),
                new ResearchEvaluation.Scenario("B-contradictory-signals", SYNTHETIC, true, false, false, true, false),
                new ResearchEvaluation.Scenario("C-missing-evidence", SYNTHETIC, false, true, false, false, false),
                new ResearchEvaluation.Scenario("D-stale-market-data", SYNTHETIC, false, false, true, true, false),
                new ResearchEvaluation.Scenario("E-comparative-markets", SYNTHETIC, true, false, false, true, false),
                new ResearchEvaluation.Scenario("F-adaptive-investigation", SYNTHETIC, false, true, false, true, true),
                new ResearchEvaluation.Scenario("G-no-follow-up", SYNTHETIC, false, false, false, true, false)
        );
    }

    public static Map<String, ResearchReport> reports(ObjectMapper mapper) {
        return Map.of(
                "A-trend-confirmation", report(mapper, List.of(market(mapper, BTC, "BTC/USD", "CURRENT",
                        List.of(evidence(mapper, BTC, "SUPPORTING")), List.of(), List.of(), List.of("breaks support"))), List.of()),
                "B-contradictory-signals", report(mapper, List.of(market(mapper, BTC, "BTC/USD", "CURRENT",
                        List.of(evidence(mapper, BTC, "SUPPORTING")),
                        List.of(evidence(mapper, BTC, "CONTRADICTING")), List.of(), List.of("fixture invalidation"))), List.of()),
                "C-missing-evidence", report(mapper, List.of(market(mapper, BTC, "BTC/USD", "CURRENT", List.of(), List.of(),
                        List.of(evidence(mapper, BTC, "MISSING")), List.of())), List.of()),
                "D-stale-market-data", report(mapper, List.of(market(mapper, BTC, "BTC/USD", "STALE",
                        List.of(evidence(mapper, BTC, "SUPPORTING")), List.of(), List.of(), List.of("fixture invalidation"))), List.of()),
                "E-comparative-markets", report(mapper, List.of(
                        market(mapper, BTC, "BTC/USD", "CURRENT", List.of(evidence(mapper, BTC, "SUPPORTING")), List.of(), List.of(), List.of("relative strength changes")),
                        market(mapper, ETH, "ETH/USD", "CURRENT", List.of(), List.of(evidence(mapper, ETH, "CONTRADICTING")), List.of(), List.of("relative strength changes"))), List.of()),
                "F-adaptive-investigation", report(mapper, List.of(adaptiveMarket(mapper)),
                         List.of(new ResearchReport.AdaptiveCapabilityRecord("market-structure-analysis", "1.1.0", BTC,
                                 "resolve trend ambiguity", "market structure levels", "COMPLETE", UUID.randomUUID(), null,
                                 "STRENGTHENED"))),
                "G-no-follow-up", report(mapper, List.of(market(mapper, BTC, "BTC/USD", "CURRENT",
                        List.of(evidence(mapper, BTC, "SUPPORTING")), List.of(), List.of(), List.of("breaks support"))), List.of()));
    }

    private static ResearchReport report(ObjectMapper mapper,
                                         List<ResearchReport.MarketResearchResult> markets,
                                         List<ResearchReport.AdaptiveCapabilityRecord> executed) {
        List<ResearchReport.AdaptiveCapabilityRecord> requested = executed.stream()
                .map(value -> new ResearchReport.AdaptiveCapabilityRecord(value.capabilityId(), value.capabilityVersion(),
                        value.marketId(), value.reason(), value.expectedEvidence(), "PROPOSED", null, null))
                .toList();
        return new ResearchReport("research-report-v2", "synthetic evaluation", "STANDARD",
                Instant.parse("2026-11-05T12:00:00Z"), Instant.parse("2026-11-05T11:00:00Z"),
                List.of(), markets, requested, executed, List.of(),
                List.of("Synthetic fixture; not a market fact."), "fixture", List.of(),
                "Advisory comparison only.");
    }

    private static ResearchReport.MarketResearchResult market(ObjectMapper mapper, UUID marketId, String instrument,
                                                              String freshness, List<JsonNode> supporting,
                                                              List<JsonNode> contradicting, List<JsonNode> missing,
                                                              List<String> invalidation) {
        return new ResearchReport.MarketResearchResult(marketId, "KRAKEN", instrument, "SPOT", "COMPLETE",
                UUID.nameUUIDFromBytes((instrument + ":thesis").getBytes()), 1L, mapper.createObjectNode(),
                freshness, mapper.createObjectNode().put("source", "synthetic-fixture")
                        .put("capabilityId", "deterministic-market-research")
                        .put("capabilityVersion", "1.0.0")
                        .put("provider", "KRAKEN")
                        .put("marketId", marketId.toString())
                        .put("analysisExecutionId", UUID.randomUUID().toString())
                        .put("evaluatedAt", "2026-11-05T12:00:00Z")
                        .put("sourceCutoff", "2026-11-05T11:00:00Z")
                        .put("inputFingerprint", "synthetic-input"),
                 supporting, contradicting, missing, invalidation, "DISABLED", null, List.of());
    }

    private static ResearchReport.MarketResearchResult adaptiveMarket(ObjectMapper mapper) {
        ResearchReport.MarketResearchResult base = market(mapper, BTC, "BTC/USD", "CURRENT", List.of(), List.of(),
                List.of(evidence(mapper, BTC, "MISSING")), List.of("fixture invalidation"));
        JsonNode followUp = mapper.createObjectNode().set("findings", mapper.createArrayNode().add(
                mapper.createObjectNode().put("findingId", FOLLOW_UP.toString())
                        .put("description", "Structure supports the initial hypothesis")
                        .put("type", "AI_FINDING")
                        .set("evidenceReferences", mapper.createArrayNode().add(FOLLOW_UP.toString()))));
        JsonNode finalInterpretation = mapper.createObjectNode().set("findings", mapper.createArrayNode().add(
                mapper.createObjectNode().put("findingId", "final-finding")
                        .put("description", "Follow-up strengthened the hypothesis")
                        .put("type", "AI_FINDING")
                        .put("title", "supporting")
                        .set("evidenceReferences", mapper.createArrayNode().add(FOLLOW_UP.toString()))));
        return new ResearchReport.MarketResearchResult(
                base.marketId(), base.provider(), base.instrument(), base.productType(), base.status(),
                base.thesisId(), base.thesisVersion(), base.deterministicAnalysis(), base.freshness(),
                base.provenance(), base.supportingEvidence(), base.contradictingEvidence(), base.missingEvidence(),
                base.invalidationConditions(), "COMPLETED", base.aiInterpretation(), base.diagnostics(),
                finalInterpretation,
                List.of(new ResearchReport.HypothesisReconciliation(
                        "hypothesis-0", "fixture hypothesis", "STRENGTHENED",
                        "Follow-up evidence strengthened the hypothesis", List.of(FOLLOW_UP))),
                followUp);
    }

    private static JsonNode evidence(ObjectMapper mapper, UUID marketId, String role) {
        return mapper.createObjectNode().put("evidenceId", UUID.nameUUIDFromBytes((marketId + ":" + role).getBytes()).toString())
                .put("role", role).put("source", "synthetic-fixture").put("reference", "fixture:" + role)
                .put("observedAt", "2026-11-05T11:00:00Z");
    }
}
