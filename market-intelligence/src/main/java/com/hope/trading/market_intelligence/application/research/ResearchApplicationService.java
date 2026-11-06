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
import com.hope.trading.market_intelligence.application.port.AiHypothesisReconciliation;
import com.hope.trading.market_intelligence.application.port.AiSynthesisContext;
import com.hope.trading.market_intelligence.application.port.RequestedAnalysis;
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
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.Set;
import java.util.function.Consumer;
import java.time.Duration;

@Service
public class ResearchApplicationService {
    private static final Logger log = LoggerFactory.getLogger(ResearchApplicationService.class);
    private static final int MAX_MARKETS = 2;
    private static final int MAX_OBJECTIVE_LENGTH = 500;
    private static final Set<String> DEPTHS = Set.of("STANDARD", "DEEP");
    private static final Map<String, String> ADAPTIVE_ALLOWLIST = Map.of(
            "market-structure-analysis", "1.1.0",
            "ohlc-momentum-analysis", "1.0.0");

    private final ResearchExecutionRepository executions;
    private final MarketDataClient marketData;
    private final MarketThesisRepository theses;
    private final CapabilityAnalysisCoordinator analysis;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final ObjectProvider<HttpAiAnalysisCapability> ai;
    private final Executor researchExecutor;

    public ResearchApplicationService(
            ResearchExecutionRepository executions,
            MarketDataClient marketData,
            MarketThesisRepository theses,
            CapabilityAnalysisCoordinator analysis,
            ObjectMapper mapper,
            Clock clock) {
        this(executions, marketData, theses, analysis, mapper, clock, null, Runnable::run);
    }

    public ResearchApplicationService(
            ResearchExecutionRepository executions,
            MarketDataClient marketData,
            MarketThesisRepository theses,
            CapabilityAnalysisCoordinator analysis,
            ObjectMapper mapper,
            Clock clock,
            ObjectProvider<HttpAiAnalysisCapability> ai) {
        this(executions, marketData, theses, analysis, mapper, clock, ai, Runnable::run);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ResearchApplicationService(
            ResearchExecutionRepository executions,
            MarketDataClient marketData,
            MarketThesisRepository theses,
            CapabilityAnalysisCoordinator analysis,
            ObjectMapper mapper,
            Clock clock,
            ObjectProvider<HttpAiAnalysisCapability> ai,
            @org.springframework.beans.factory.annotation.Qualifier("researchExecutor") Executor researchExecutor) {
        this.executions = executions;
        this.marketData = marketData;
        this.theses = theses;
        this.analysis = analysis;
        this.mapper = mapper;
        this.clock = clock;
        this.ai = ai;
        this.researchExecutor = researchExecutor;
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

        ResearchExecution requested = execution;
        enqueue(requested.researchId());
        return executions.findById(requested.researchId()).orElse(requested);
    }

    private void execute(UUID researchId) {
        ResearchExecution execution = executions.claimRequested(researchId, clock.instant()).orElse(null);
        if (execution == null) {
            return;
        }
        try {
            ResearchReport report = buildReport(
                    new ResearchRequest(execution.marketIds(), execution.objective(), execution.depth()),
                    execution.researchId(), partial -> executions.saveIfStatus(
                            execution.checkpoint(mapper.valueToTree(partial), clock.instant()),
                            com.hope.trading.market_intelligence.domain.research.ResearchExecutionStatus.RUNNING));
            JsonNode reportJson = mapper.valueToTree(report);
            ResearchExecution terminal = report.markets().stream().allMatch(
                    value -> "COMPLETE".equals(value.status()))
                    ? execution.complete(reportJson, clock.instant())
                    : execution.partial(reportJson, clock.instant());
            executions.saveIfStatus(terminal,
                    com.hope.trading.market_intelligence.domain.research.ResearchExecutionStatus.RUNNING);
        } catch (ResearchException exception) {
            executions.saveIfStatus(execution.fail(exception.code(), clock.instant()),
                    com.hope.trading.market_intelligence.domain.research.ResearchExecutionStatus.RUNNING);
        } catch (RuntimeException exception) {
            log.warn("Research execution failed researchId={} errorType={} message={}",
                    execution.researchId(), exception.getClass().getSimpleName(), exception.getMessage());
            executions.saveIfStatus(execution.fail("RESEARCH_EXECUTION_FAILED", clock.instant()),
                    com.hope.trading.market_intelligence.domain.research.ResearchExecutionStatus.RUNNING);
        }
    }

    public void dispatchPending() {
        executions.findByStatus(com.hope.trading.market_intelligence.domain.research.ResearchExecutionStatus.REQUESTED)
                .forEach(value -> enqueue(value.researchId()));
        Instant cutoff = clock.instant().minus(Duration.ofMinutes(5));
        executions.findByStatus(com.hope.trading.market_intelligence.domain.research.ResearchExecutionStatus.RUNNING)
                .stream().filter(value -> value.updatedAt().isBefore(cutoff))
                .forEach(value -> {
                    if (executions.saveIfStatus(value.requeue(clock.instant()),
                            com.hope.trading.market_intelligence.domain.research.ResearchExecutionStatus.RUNNING)) {
                        enqueue(value.researchId());
                    }
                });
    }

    private void enqueue(UUID researchId) {
        try {
            researchExecutor.execute(() -> execute(researchId));
        } catch (RejectedExecutionException rejected) {
            log.warn("Research execution queue is full researchId={}", researchId);
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

    private ResearchReport buildReport(ResearchRequest request, UUID researchId,
                                       Consumer<ResearchReport> checkpoint) {
        List<ResearchReport.MarketResearchResult> results = new ArrayList<>();
        List<ResearchReport.AdaptiveCapabilityRecord> requestedCapabilities = new ArrayList<>();
        List<ResearchReport.AdaptiveCapabilityRecord> executedCapabilities = new ArrayList<>();
        List<ResearchReport.AdaptiveCapabilityRecord> rejectedCapabilities = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        boolean followUpUsed = false;
        boolean initialAiUsed = false;
        boolean finalAiUsed = false;
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
                checkpoint.accept(buildReportSnapshot(request, results, requestedCapabilities,
                        executedCapabilities, rejectedCapabilities, diagnostics));
                continue;
            }
            if (market == null || !marketId.equals(market.marketId())) {
                results.add(new ResearchReport.MarketResearchResult(
                        marketId, null, null, null, "FAILED", null, null, null,
                        "UNKNOWN", null, List.of(), List.of(), List.of(), List.of(),
                        "NOT_REQUESTED", null,
                        List.of("MARKET_NOT_FOUND")));
                checkpoint.accept(buildReportSnapshot(request, results, requestedCapabilities,
                        executedCapabilities, rejectedCapabilities, diagnostics));
                continue;
            }
            if (isBlank(market.provider()) || isBlank(market.symbol()) || isBlank(market.productType())) {
                results.add(new ResearchReport.MarketResearchResult(
                        marketId, market.provider(), market.symbol(), market.productType(), "FAILED",
                        null, null, null, "UNKNOWN", null, List.of(), List.of(), List.of(), List.of(),
                        "NOT_REQUESTED", null, List.of("MARKET_IDENTITY_INCOMPLETE")));
                checkpoint.accept(buildReportSnapshot(request, results, requestedCapabilities,
                        executedCapabilities, rejectedCapabilities, diagnostics));
                continue;
            }
            Optional<MarketThesis> thesis = latestThesis(marketId);
            List<String> marketDiagnostics = new ArrayList<>();
            ConsolidatedIntelligence deterministic = null;
            String aiStatus = "DISABLED";
            JsonNode aiInterpretation = null;
            JsonNode finalAiInterpretation = null;
            List<ResearchReport.HypothesisReconciliation> reconciliations = List.of();
            JsonNode followUpEvidence = null;
            String freshness = thesis.map(value -> value.validUntil()
                            .map(until -> clock.instant().isBefore(until) ? "CURRENT" : "STALE")
                            .orElse("UNKNOWN"))
                    .orElse("UNKNOWN");
            UUID deterministicAnalysisExecutionId = UUID.randomUUID();
            JsonNode provenance = mapper.valueToTree(Map.ofEntries(
                    Map.entry("source", "market-intelligence"),
                    Map.entry("capabilityId", "deterministic-market-research"),
                    Map.entry("capabilityVersion", "1.0.0"),
                    Map.entry("provider", market.provider()),
                    Map.entry("marketId", market.marketId().toString()),
                    Map.entry("thesisId", thesis.map(value -> value.id().toString()).orElse("MISSING")),
                    Map.entry("thesisLineageId", thesis.map(value -> value.lineageId() == null
                            ? "MISSING" : value.lineageId().toString()).orElse("MISSING")),
                    Map.entry("thesisVersion", thesis.map(value -> Long.toString(value.version())).orElse("MISSING")),
                    Map.entry("analysisExecutionId", deterministicAnalysisExecutionId.toString()),
                    Map.entry("evaluatedAt", clock.instant().toString()),
                    Map.entry("sourceCutoff", thesis.map(value -> value.sourceCutoff().toString()).orElse("MISSING")),
                    Map.entry("inputFingerprint", thesis.map(value -> value.inputFingerprint() == null
                            ? "MISSING" : value.inputFingerprint()).orElse("MISSING"))));
            try {
                deterministic = analysis.analyze(deterministicAnalysisExecutionId, new IntelligenceAnalysisRequest(
                         deterministicAnalysisExecutionId, marketId, AnalysisExecutionMode.PASSIVE,
                         request.objective(), null, researchId, null, null,
                         EvidenceAcquisitionPolicy.LIVE_CURRENT, thesis.orElse(null)));
            } catch (RuntimeException exception) {
                marketDiagnostics.add("DETERMINISTIC_ANALYSIS_FAILED");
            }
            if (thesis.isEmpty()) {
                marketDiagnostics.add("MARKET_THESIS_MISSING");
                aiStatus = "NOT_REQUESTED";
            } else if (ai != null && ai.getIfAvailable() != null && !initialAiUsed) {
                HttpAiAnalysisCapability aiCapability = ai.getIfAvailable();
                initialAiUsed = true;
                try {
                    CapabilityAnalysisResult interpreted = aiCapability.analyzeAdaptive(
                            new IntelligenceAnalysisRequest(
                                    UUID.randomUUID(), marketId, AnalysisExecutionMode.PASSIVE,
                                     request.objective(), null, researchId, null, null,
                                    EvidenceAcquisitionPolicy.LIVE_CURRENT, thesis.orElseThrow()),
                            new com.hope.trading.market_intelligence.domain.IntelligenceContext(Map.of()));
                    RequestedAnalysis requested = interpreted.requestedAnalysis();
                    ConsolidatedIntelligence followUp = null;
                    UUID followUpId = null;
                    String followUpStatus = null;
                    if (requested != null) {
                        ResearchReport.AdaptiveCapabilityRecord requestedRecord =
                                new ResearchReport.AdaptiveCapabilityRecord(
                                        requested.capabilityId(), requested.capabilityVersion(),
                                        requested.marketId(), requested.reason(), requested.expectedEvidence(),
                                        "PROPOSED", null, null);
                        requestedCapabilities.add(requestedRecord);
                        String rejection = null;
                        if (followUpUsed) {
                            rejection = "ADAPTIVE_BUDGET_EXHAUSTED";
                        } else if (!validAdaptiveText(requested.capabilityId(), 120)
                                || !validAdaptiveText(requested.capabilityVersion(), 32)
                                || requested.marketId() == null
                                || !validAdaptiveText(requested.reason(), 2_000)
                                || !validAdaptiveText(requested.expectedEvidence(), 2_000)) {
                            rejection = "ADAPTIVE_REQUEST_MALFORMED";
                        } else {
                            String expectedVersion = ADAPTIVE_ALLOWLIST.get(requested.capabilityId());
                            if (expectedVersion == null || !expectedVersion.equals(requested.capabilityVersion())) {
                                rejection = "CAPABILITY_NOT_ALLOWLISTED";
                            } else if (!marketId.equals(requested.marketId())) {
                                rejection = "CAPABILITY_MARKET_OUT_OF_SCOPE";
                            }
                        }
                        if (rejection != null) {
                            rejectedCapabilities.add(new ResearchReport.AdaptiveCapabilityRecord(
                                    requested.capabilityId(), requested.capabilityVersion(), requested.marketId(),
                                    requested.reason(), requested.expectedEvidence(), "REJECTED", null,
                                    rejection));
                            marketDiagnostics.add(rejection);
                        } else {
                            followUpUsed = true;
                            followUpId = UUID.randomUUID();
                            try {
                                followUp = analysis.analyze(followUpId,
                                        new IntelligenceAnalysisRequest(
                                                followUpId, marketId, AnalysisExecutionMode.PASSIVE,
                                                 requested.reason(), null, researchId, null,
                                                 null, EvidenceAcquisitionPolicy.LIVE_CURRENT,
                                                thesis.orElseThrow()), Set.of(requested.capabilityId()));
                                followUpStatus = followUp == null ? "FAILED" : followUp.status().name();
                            } catch (RuntimeException exception) {
                                followUpStatus = "FAILED";
                                marketDiagnostics.add("FOLLOW_UP_ANALYSIS_FAILED");
                            }
                            if (followUp != null) {
                                followUpEvidence = mapper.valueToTree(followUp);
                            }
                        }
                    }
                    if (interpreted.findings().isEmpty()) {
                        aiStatus = interpreted.warnings().isEmpty() ? "PARTIAL" : "FAILED";
                        if (!interpreted.warnings().isEmpty()) {
                            marketDiagnostics.add("AI_INTERPRETATION_WARNING");
                        }
                    } else {
                        aiStatus = interpreted.warnings().isEmpty() ? "COMPLETED" : "PARTIAL";
                    }
                    aiInterpretation = mapper.valueToTree(interpreted);
                    if (!finalAiUsed && requested != null && followUpId != null && followUp != null
                            && !interpreted.findings().isEmpty()) {
                        finalAiUsed = true;
                        AiSynthesisContext synthesisContext = synthesisContext(
                                request, marketId, thesis.orElseThrow(), interpreted, requested,
                                followUpId, followUp);
                        followUpEvidence = mapper.valueToTree(synthesisContext.followUpEvidence());
                        try {
                            CapabilityAnalysisResult finalInterpretation = aiCapability.analyzeFinal(
                                    new IntelligenceAnalysisRequest(
                                            UUID.randomUUID(), marketId, AnalysisExecutionMode.PASSIVE,
                                             request.objective(), null, researchId, null, null,
                                            EvidenceAcquisitionPolicy.LIVE_CURRENT, thesis.orElseThrow()),
                                    new com.hope.trading.market_intelligence.domain.IntelligenceContext(Map.of()),
                                    synthesisContext);
                            if (finalInterpretation == null || finalInterpretation.requestedAnalysis() != null) {
                                marketDiagnostics.add("FINAL_SYNTHESIS_UNSUPPORTED");
                            } else if (finalInterpretation.findings().isEmpty()
                                    || !validReconciliations(finalInterpretation.reconciliations(), synthesisContext,
                                    thesis.orElseThrow().evidence().stream()
                                            .map(com.hope.trading.market_intelligence.domain.thesis.MarketThesisEvidence::evidenceId)
                                            .collect(java.util.stream.Collectors.toSet()))) {
                                marketDiagnostics.add("FINAL_SYNTHESIS_UNSUPPORTED");
                            } else {
                                finalAiInterpretation = mapper.valueToTree(finalInterpretation);
                                reconciliations = finalInterpretation.reconciliations().stream()
                                        .map(this::toReportReconciliation).toList();
                                aiStatus = finalInterpretation.warnings().isEmpty() ? "COMPLETED" : "PARTIAL";
                            }
                        } catch (RuntimeException exception) {
                            marketDiagnostics.add("FINAL_SYNTHESIS_FAILED");
                        }
                    }
                    if (requested != null && followUpId != null) {
                        String effect = finalAiInterpretation == null
                                ? "FINAL_SYNTHESIS_FAILED" : interpretationEffect(reconciliations);
                        executedCapabilities.add(new ResearchReport.AdaptiveCapabilityRecord(
                                requested.capabilityId(), requested.capabilityVersion(), requested.marketId(),
                                requested.reason(), requested.expectedEvidence(), followUpStatus,
                                followUpId, finalAiInterpretation == null ? "FINAL_SYNTHESIS_UNAVAILABLE" : null,
                                effect));
                    }
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
                     marketDiagnostics,
                     finalAiInterpretation,
                      reconciliations,
                      followUpEvidence));
            checkpoint.accept(buildReportSnapshot(request, results, requestedCapabilities,
                    executedCapabilities, rejectedCapabilities, diagnostics));
        }
        return buildReportSnapshot(request, results, requestedCapabilities,
                executedCapabilities, rejectedCapabilities, diagnostics);
    }

    private ResearchReport buildReportSnapshot(
            ResearchRequest request,
            List<ResearchReport.MarketResearchResult> results,
            List<ResearchReport.AdaptiveCapabilityRecord> requestedCapabilities,
            List<ResearchReport.AdaptiveCapabilityRecord> executedCapabilities,
            List<ResearchReport.AdaptiveCapabilityRecord> rejectedCapabilities,
            List<String> diagnostics) {
        long completed = results.stream().filter(value -> "COMPLETE".equals(value.status())).count();
        Instant sourceCutoff = request.marketIds().stream()
                .flatMap(value -> latestThesis(value).stream())
                .map(MarketThesis::sourceCutoff)
                .max(Comparator.naturalOrder())
                .orElse(null);
        List<String> hypotheses = request.marketIds().stream()
                .flatMap(value -> latestThesis(value).flatMap(MarketThesis::scenario).stream())
                .map(value -> value.statement()).toList();
        List<String> reportDiagnostics = new ArrayList<>(diagnostics);
        if (completed == 0) reportDiagnostics.add("NO_MARKET_ANALYSIS_COMPLETED");
        if (results.stream().allMatch(value -> "DISABLED".equals(value.aiStatus())
                || "NOT_REQUESTED".equals(value.aiStatus()))) {
            reportDiagnostics.add("AI_INTERPRETATION_DISABLED");
        }
        String conclusion = completed == results.size()
                ? "Deterministic comparison completed; no trade authorization was produced."
                : "Research is partial; missing or failed market evidence must be reviewed.";
        return new ResearchReport("research-report-v2", request.objective(), request.depth(),
                clock.instant(), sourceCutoff, hypotheses, results,
                requestedCapabilities, executedCapabilities, rejectedCapabilities,
                List.of("This report is advisory and does not authorize a trade.",
                        "No calibrated probability or profitability claim is produced."),
                "existing-ai-engine-contract:v1", reportDiagnostics, conclusion);
    }

    private AiSynthesisContext synthesisContext(
            ResearchRequest request, UUID marketId, MarketThesis thesis,
            CapabilityAnalysisResult initial, RequestedAnalysis requested,
            UUID followUpId, ConsolidatedIntelligence followUp) {
        List<AiSynthesisContext.AiSynthesisFinding> initialFindings = initial.findings().stream()
                .map(value -> new AiSynthesisContext.AiSynthesisFinding(
                        value.findingId(), value.title(), value.description(),
                        value.evidenceReferences().stream().toList()))
                .toList();
        List<AiSynthesisContext.AiSynthesisFinding> followUpFindings = followUp.findings().stream()
                .map(value -> {
                    UUID generatedEvidenceId = UUID.nameUUIDFromBytes(
                            (followUpId + ":" + value.findingId()).getBytes(StandardCharsets.UTF_8));
                    Set<UUID> evidence = new LinkedHashSet<>(value.evidenceReferences());
                    evidence.add(generatedEvidenceId);
                    return new AiSynthesisContext.AiSynthesisFinding(
                            generatedEvidenceId.toString(), value.title(), value.description(), evidence.stream().toList());
                }).toList();
        return new AiSynthesisContext(
                request.objective(), marketId,
                thesis.scenario().map(value -> List.of(value.statement())).orElse(List.of()),
                initialFindings, requested,
                new AiSynthesisContext.FollowUpEvidence(
                        followUpId, requested.capabilityId(), requested.capabilityVersion(),
                        followUp.status().name(),
                        followUp.executionMetadata() == null ? clock.instant() : followUp.executionMetadata().completedAt(),
                        thesis.inputFingerprint(), followUpFindings, followUp.warnings()));
    }

    private boolean validReconciliations(
            List<AiHypothesisReconciliation> reconciliations, AiSynthesisContext context,
            Set<UUID> thesisEvidenceIds) {
        Set<UUID> allowed = new HashSet<>();
        allowed.addAll(thesisEvidenceIds);
        boolean initialReferencesValid = context.initialFindings().stream()
                .allMatch(value -> thesisEvidenceIds.containsAll(value.evidenceReferences()));
        if (!initialReferencesValid) return false;
        context.initialFindings().forEach(value -> allowed.addAll(value.evidenceReferences()));
        context.followUpEvidence().findings().forEach(value -> allowed.addAll(value.evidenceReferences()));
        Set<UUID> followUpEvidenceIds = context.followUpEvidence().findings().stream()
                .map(AiSynthesisContext.AiSynthesisFinding::findingId)
                .map(this::parseUuid)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> effects = Set.of("CONFIRMED", "STRENGTHENED", "WEAKENED", "INVALIDATED",
                "UNCHANGED", "INCONCLUSIVE");
        return reconciliations.stream().allMatch(value -> value.evidenceReferences() != null
                && validAdaptiveText(value.hypothesisId(), 200)
                && validAdaptiveText(value.hypothesis(), 4_000)
                && validAdaptiveText(value.explanation(), 4_000)
                && effects.contains(value.effect())
                && ("UNCHANGED".equals(value.effect()) || "INCONCLUSIVE".equals(value.effect())
                || (!value.evidenceReferences().isEmpty()
                && !Collections.disjoint(value.evidenceReferences(), followUpEvidenceIds)))
                && allowed.containsAll(value.evidenceReferences()));
    }

    private ResearchReport.HypothesisReconciliation toReportReconciliation(
            AiHypothesisReconciliation value) {
        return new ResearchReport.HypothesisReconciliation(value.hypothesisId(), value.hypothesis(),
                value.effect(), value.explanation(), value.evidenceReferences());
    }

    private String interpretationEffect(List<ResearchReport.HypothesisReconciliation> reconciliations) {
        if (reconciliations.isEmpty()
                || reconciliations.stream().allMatch(value -> "UNCHANGED".equals(value.effect()))) {
            return "NO_MATERIAL_CHANGE";
        }
        if (reconciliations.stream().allMatch(value -> "INCONCLUSIVE".equals(value.effect()))) {
            return "INCONCLUSIVE";
        }
        if (reconciliations.stream().allMatch(value -> "CONFIRMED".equals(value.effect()))) {
            return "CONFIRMED";
        }
        if (reconciliations.stream().allMatch(value -> "STRENGTHENED".equals(value.effect()))) {
            return "STRENGTHENED";
        }
        if (reconciliations.stream().allMatch(value -> "WEAKENED".equals(value.effect()))) {
            return "WEAKENED";
        }
        if (reconciliations.stream().allMatch(value -> "INVALIDATED".equals(value.effect()))) {
            return "INVALIDATED";
        }
        return "SUPPORTED_REVISION";
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            return null;
        }
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

    private boolean validAdaptiveText(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength;
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
