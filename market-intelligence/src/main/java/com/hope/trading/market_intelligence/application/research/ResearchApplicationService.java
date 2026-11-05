package com.hope.trading.market_intelligence.application.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.adapter.marketdata.MarketDataClient;
import com.hope.trading.market_intelligence.adapter.marketdata.MarketResponse;
import com.hope.trading.market_intelligence.application.execution.CapabilityAnalysisCoordinator;
import com.hope.trading.market_intelligence.application.capability.CapabilityAnalysisResult;
import com.hope.trading.market_intelligence.application.capability.HttpAiAnalysisCapability;
import com.hope.trading.market_intelligence.application.port.MarketThesisRepository;
import com.hope.trading.market_intelligence.application.port.ResearchExecutionRepository;
import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.ConsolidatedIntelligence;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.IntelligenceAnalysisRequest;
import com.hope.trading.market_intelligence.domain.research.ResearchExecution;
import com.hope.trading.market_intelligence.domain.research.ResearchReport;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesis;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
public class ResearchApplicationService {
    private static final Logger log = LoggerFactory.getLogger(ResearchApplicationService.class);
    private static final int MAX_MARKETS = 2;
    private static final int MAX_OBJECTIVE_LENGTH = 500;
    private static final Set<String> DEPTHS = Set.of("STANDARD", "DEEP");

    private final ResearchExecutionRepository executions;
    private final MarketDataClient marketData;
    private final MarketThesisRepository theses;
    private final CapabilityAnalysisCoordinator analysis;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final ObjectProvider<HttpAiAnalysisCapability> ai;

    public ResearchApplicationService(
            ResearchExecutionRepository executions,
            MarketDataClient marketData,
            MarketThesisRepository theses,
            CapabilityAnalysisCoordinator analysis,
            ObjectMapper mapper,
            Clock clock) {
        this(executions, marketData, theses, analysis, mapper, clock, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ResearchApplicationService(
            ResearchExecutionRepository executions,
            MarketDataClient marketData,
            MarketThesisRepository theses,
            CapabilityAnalysisCoordinator analysis,
            ObjectMapper mapper,
            Clock clock,
            ObjectProvider<HttpAiAnalysisCapability> ai) {
        this.executions = executions;
        this.marketData = marketData;
        this.theses = theses;
        this.analysis = analysis;
        this.mapper = mapper;
        this.clock = clock;
        this.ai = ai;
    }

    public ResearchExecution start(UUID actorId, String idempotencyKey, ResearchRequest request) {
        validate(actorId, idempotencyKey, request);
        String fingerprint = fingerprint(actorId, request);
        Optional<ResearchExecution> existing = executions.findByActorIdAndIdempotencyKey(
                actorId, idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().requestFingerprint().equals(fingerprint)) {
                throw new ResearchException("IDEMPOTENCY_CONFLICT",
                        "Idempotency-Key is already bound to another research request", 409);
            }
            return existing.get();
        }

        Instant now = clock.instant();
        ResearchExecution execution = ResearchExecution.requested(
                UUID.randomUUID(), actorId, idempotencyKey, fingerprint,
                request.marketIds(), request.objective(), request.depth(), now);
        try {
            execution = executions.save(execution);
        } catch (DataIntegrityViolationException race) {
            ResearchExecution winner = executions.findByActorIdAndIdempotencyKey(actorId, idempotencyKey)
                    .orElseThrow(() -> race);
            if (!winner.requestFingerprint().equals(fingerprint)) {
                throw new ResearchException("IDEMPOTENCY_CONFLICT",
                        "Idempotency-Key is already bound to another research request", 409);
            }
            return winner;
        }

        execution = executions.save(execution.running(clock.instant()));
        try {
            ResearchReport report = buildReport(request, execution.researchId());
            JsonNode reportJson = mapper.valueToTree(report);
            return executions.save(report.markets().stream().allMatch(
                    value -> "COMPLETE".equals(value.status()))
                    ? execution.complete(reportJson, clock.instant())
                    : execution.partial(reportJson, clock.instant()));
        } catch (ResearchException exception) {
            return executions.save(execution.fail(exception.code(), clock.instant()));
        } catch (RuntimeException exception) {
            log.warn("Research execution failed researchId={} errorType={} message={}",
                    execution.researchId(), exception.getClass().getSimpleName(), exception.getMessage());
            return executions.save(execution.fail("RESEARCH_EXECUTION_FAILED", clock.instant()));
        }
    }

    @Transactional(readOnly = true)
    public ResearchExecution findOwned(UUID actorId, UUID researchId) {
        ResearchExecution execution = executions.findById(researchId)
                .orElseThrow(() -> notFound(researchId));
        if (!execution.actorId().equals(actorId)) {
            throw notFound(researchId);
        }
        return execution;
    }

    private ResearchReport buildReport(ResearchRequest request, UUID researchId) {
        List<ResearchReport.MarketResearchResult> results = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        for (UUID marketId : request.marketIds()) {
            MarketResponse market;
            try {
                market = marketData.findMarket(marketId);
            } catch (RuntimeException exception) {
                results.add(new ResearchReport.MarketResearchResult(
                        marketId, null, null, null, "FAILED", null, null, null,
                        "UNKNOWN", null, List.of(), List.of(), List.of(), List.of(),
                        "NOT_REQUESTED", null,
                        List.of("MARKET_UNAVAILABLE")));
                continue;
            }
            if (market == null || !marketId.equals(market.marketId())) {
                results.add(new ResearchReport.MarketResearchResult(
                        marketId, null, null, null, "FAILED", null, null, null,
                        "UNKNOWN", null, List.of(), List.of(), List.of(), List.of(),
                        "NOT_REQUESTED", null,
                        List.of("MARKET_NOT_FOUND")));
                continue;
            }
            if (isBlank(market.provider()) || isBlank(market.symbol()) || isBlank(market.productType())) {
                results.add(new ResearchReport.MarketResearchResult(
                        marketId, market.provider(), market.symbol(), market.productType(), "FAILED",
                        null, null, null, "UNKNOWN", null, List.of(), List.of(), List.of(), List.of(),
                        "NOT_REQUESTED", null, List.of("MARKET_IDENTITY_INCOMPLETE")));
                continue;
            }
            Optional<MarketThesis> thesis = latestThesis(marketId);
            List<String> marketDiagnostics = new ArrayList<>();
            ConsolidatedIntelligence deterministic = null;
            String aiStatus = "DISABLED";
            JsonNode aiInterpretation = null;
            String freshness = thesis.map(value -> value.validUntil()
                            .map(until -> clock.instant().isBefore(until) ? "CURRENT" : "STALE")
                            .orElse("UNKNOWN"))
                    .orElse("UNKNOWN");
            JsonNode provenance = mapper.valueToTree(Map.of(
                    "source", "market-intelligence",
                    "provider", market.provider(),
                    "marketId", market.marketId().toString(),
                    "thesisId", thesis.map(value -> value.id().toString()).orElse("MISSING")));
            try {
                UUID analysisId = UUID.randomUUID();
                deterministic = analysis.analyze(analysisId, new IntelligenceAnalysisRequest(
                        analysisId, marketId, AnalysisExecutionMode.PASSIVE,
                        request.objective(), null, researchId, null, clock.instant(),
                        EvidenceAcquisitionPolicy.LIVE_CURRENT, thesis.orElse(null)));
            } catch (RuntimeException exception) {
                marketDiagnostics.add("DETERMINISTIC_ANALYSIS_FAILED");
            }
            if (thesis.isEmpty()) {
                marketDiagnostics.add("MARKET_THESIS_MISSING");
                aiStatus = "NOT_REQUESTED";
            } else if (ai != null && ai.getIfAvailable() != null) {
                try {
                    CapabilityAnalysisResult interpreted = ai.getIfAvailable().analyze(
                            new IntelligenceAnalysisRequest(
                                    UUID.randomUUID(), marketId, AnalysisExecutionMode.PASSIVE,
                                    request.objective(), null, researchId, null, clock.instant(),
                                    EvidenceAcquisitionPolicy.LIVE_CURRENT, thesis.orElseThrow()),
                            new com.hope.trading.market_intelligence.domain.IntelligenceContext(Map.of()));
                    aiStatus = interpreted.findings().isEmpty() ? "PARTIAL" : "COMPLETED";
                    aiInterpretation = mapper.valueToTree(interpreted);
                } catch (RuntimeException exception) {
                    aiStatus = "FAILED";
                    marketDiagnostics.add("AI_INTERPRETATION_FAILED");
                }
            }
            results.add(new ResearchReport.MarketResearchResult(
                    market.marketId(), market.provider(), market.symbol(), market.productType(),
                    deterministic == null ? "FAILED" : deterministic.status().name(),
                    thesis.map(MarketThesis::id).orElse(null),
                    thesis.map(MarketThesis::version).orElse(null),
                    deterministic == null ? null : mapper.valueToTree(deterministic),
                    freshness,
                    provenance,
                    thesis.map(value -> value.supportingEvidence().stream()
                            .map(evidence -> (JsonNode) mapper.valueToTree(evidence)).toList()).orElse(List.<JsonNode>of()),
                    thesis.map(value -> value.contradictingEvidence().stream()
                            .map(evidence -> (JsonNode) mapper.valueToTree(evidence)).toList()).orElse(List.<JsonNode>of()),
                    thesis.map(value -> value.missingEvidence().stream()
                            .map(evidence -> (JsonNode) mapper.valueToTree(evidence)).toList()).orElse(List.<JsonNode>of()),
                    thesis.flatMap(MarketThesis::scenario)
                            .map(value -> value.invalidationConditions().stream()
                                    .map(condition -> condition.explanation()).toList())
                            .orElse(List.of()),
                    aiStatus,
                    aiInterpretation,
                    marketDiagnostics));
        }
        long completed = results.stream().filter(value -> "COMPLETE".equals(value.status())).count();
        Instant sourceCutoff = request.marketIds().stream()
                .flatMap(value -> latestThesis(value).stream())
                .map(MarketThesis::sourceCutoff)
                .max(Comparator.naturalOrder())
                .orElse(null);
        List<String> hypotheses = request.marketIds().stream()
                .flatMap(value -> latestThesis(value).flatMap(MarketThesis::scenario).stream())
                .map(value -> value.statement()).toList();
        if (completed == 0) diagnostics.add("NO_MARKET_ANALYSIS_COMPLETED");
        if (results.stream().allMatch(value -> "DISABLED".equals(value.aiStatus())
                || "NOT_REQUESTED".equals(value.aiStatus()))) {
            diagnostics.add("AI_INTERPRETATION_DISABLED");
        }
        String conclusion = completed == results.size()
                ? "Deterministic comparison completed; no trade authorization was produced."
                : "Research is partial; missing or failed market evidence must be reviewed.";
        return new ResearchReport("research-report-v2", request.objective(), request.depth(),
                clock.instant(), sourceCutoff, hypotheses, results,
                List.of("This report is advisory and does not authorize a trade.",
                        "No calibrated probability or profitability claim is produced."),
                "existing-ai-engine-contract:v1", diagnostics, conclusion);
    }

    private Optional<MarketThesis> latestThesis(UUID marketId) {
        return theses.findByMarket(marketId).stream()
                .max(Comparator.comparing(MarketThesis::version));
    }

    private void validate(UUID actorId, String idempotencyKey, ResearchRequest request) {
        if (actorId == null) throw new ResearchException("AUTHENTICATION_REQUIRED",
                "Authenticated actor context is required", 401);
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 200) {
            throw new ResearchException("INVALID_IDEMPOTENCY_KEY", "A bounded Idempotency-Key is required", 400);
        }
        if (request == null || request.marketIds() == null || request.marketIds().size() != MAX_MARKETS
                || new HashSet<>(request.marketIds()).size() != request.marketIds().size()) {
            throw new ResearchException("INVALID_MARKET_SCOPE", "Exactly two distinct market IDs are required", 400);
        }
        if (request.objective() == null || request.objective().isBlank()
                || request.objective().length() > MAX_OBJECTIVE_LENGTH) {
            throw new ResearchException("INVALID_OBJECTIVE", "Objective must be between 1 and 500 characters", 400);
        }
        if (!DEPTHS.contains(request.depth())) {
            throw new ResearchException("INVALID_RESEARCH_DEPTH", "Depth must be STANDARD or DEEP", 400);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String fingerprint(UUID actorId, ResearchRequest request) {
        String value = actorId + "|" + request.marketIds() + "|" + request.objective() + "|" + request.depth();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private ResearchException notFound(UUID researchId) {
        return new ResearchException("RESEARCH_NOT_FOUND", "Research not found: " + researchId, 404);
    }

    public record ResearchRequest(List<UUID> marketIds, String objective, String depth) {
        public ResearchRequest {
            marketIds = marketIds == null ? null : List.copyOf(marketIds);
            depth = depth == null ? "STANDARD" : depth.trim().toUpperCase(Locale.ROOT);
        }
    }
}
