package com.hope.trading.market_intelligence.application.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hope.trading.market_intelligence.adapter.tradingcore.ResearchAuthorizationClient;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.feature.FeatureMissingness;
import com.hope.trading.market_intelligence.domain.outcome.OutcomeHorizon;
import com.hope.trading.market_intelligence.domain.research.HistoricalDatasetExportJobStatus;
import com.hope.trading.market_intelligence.domain.research.ResearchFeatureRequirements;
import com.hope.trading.market_intelligence.domain.research.ResearchFeatureSnapshotKey;
import com.hope.trading.market_intelligence.domain.research.ResearchOutcomeKey;
import com.hope.trading.market_intelligence.domain.research.ResearchPopulationSpec;
import com.hope.trading.market_intelligence.domain.research.ResearchSamplingCadence;
import com.hope.trading.market_intelligence.application.research.histdata.Ml01HistDataPreparation;
import com.hope.trading.market_intelligence.application.research.histdata.Ml01ExploratoryH1AdmissibilityProfile;
import com.hope.trading.market_intelligence.application.research.histdata.HistoricalClosureEvidenceAdapter;
import com.hope.trading.market_intelligence.application.research.histdata.HistoricalClosureEvidenceSource;
import com.hope.trading.market_intelligence.infrastructure.research.HistoricalDatasetExportJobEntity;
import com.hope.trading.market_intelligence.infrastructure.research.HistoricalDatasetExportJobRepository;
import com.hope.trading.market_intelligence.security.MiAgentPrincipal;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.HexFormat;

@Service
public class HistoricalDatasetExportApplicationService {
    private static final long MAX_ROWS = 100_000;
    private static final long MAX_BYTES = 100L * 1024L * 1024L;
    private static final long MAX_DURATION_SECONDS = 3600;
    private final HistoricalDatasetExportJobRepository jobs;
    private final ResearchAuthorizationClient authorization;
    private final ResearchDatasetExportService exporter;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final Executor executor;
    private final Path outputRoot;
    private final Path ml01HistDataRoot;
    private final HistoricalClosureEvidenceSource closureEvidenceSource;

    public HistoricalDatasetExportApplicationService(
            HistoricalDatasetExportJobRepository jobs,
            ResearchAuthorizationClient authorization,
            ResearchDatasetExportService exporter,
            ObjectMapper mapper,
            Clock clock,
            @Qualifier("researchExecutor") Executor executor,
            @Value("${research.dataset.agent-export-root:./var/research-agent-exports}") String outputRoot,
            @Value("${research.ml01.histdata-root:./histData}") String ml01HistDataRoot,
            HistoricalClosureEvidenceSource closureEvidenceSource) {
        this.jobs = jobs;
        this.authorization = authorization;
        this.exporter = exporter;
        this.mapper = mapper;
        this.clock = clock;
        this.executor = executor;
        this.outputRoot = Path.of(outputRoot).toAbsolutePath().normalize();
        this.ml01HistDataRoot = Path.of(ml01HistDataRoot).toAbsolutePath().normalize();
        this.closureEvidenceSource = closureEvidenceSource;
    }

    @Transactional
    public HistoricalDatasetExportResponse submit(MiAgentPrincipal agent, UUID scopeId,
                                                   HistoricalDatasetExportRequest request,
                                                   String idempotencyKey, String correlationId) {
        if (agent == null || request == null || idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.length() > 200 || request.maxRows() > MAX_ROWS
                || request.maxBytes() > MAX_BYTES || request.maxDurationSeconds() > MAX_DURATION_SECONDS) {
            throw new HistoricalResearchException(400, "INVALID_EXPORT_REQUEST", "invalid export request");
        }
        if (serialize(request.population()).length() > 1_000_000) {
            throw new HistoricalResearchException(400, "INVALID_POPULATION", "population specification is too large");
        }
        Ml01ExportSpec ml01 = readMl01ExportSpec(request.population());
        var decision = ml01 == null
                ? authorize(agent, scopeId, "research:export", readPopulation(request.population()), request, correlationId)
                : authorize(agent, scopeId, "research:export", ml01, request, correlationId);
        String fingerprint = fingerprint(scopeId, agent, decision, request);
        var existing = jobs.findByAgentIdAndIdempotencyKey(agent.agentId(), idempotencyKey).orElse(null);
        if (existing != null) {
            if (!existing.requestFingerprint().equals(fingerprint)) {
                throw new HistoricalResearchException(409, "IDEMPOTENCY_CONFLICT", "idempotency key was reused with a different request");
            }
            return response(existing);
        }
        Instant now = clock.instant();
        var job = HistoricalDatasetExportJobEntity.create(decision.ownerUserId(), agent.agentId(),
                scopeId, decision.grantId(), decision.grantVersion(),
                agent.credentialId(), agent.credentialVersion(), idempotencyKey, fingerprint,
                serialize(request), correlationId, now,
                now.plusSeconds(Math.min(request.maxDurationSeconds(), 3600)));
        try {
            jobs.saveAndFlush(job);
        } catch (DataIntegrityViolationException duplicate) {
            var raced = jobs.findByAgentIdAndIdempotencyKey(agent.agentId(), idempotencyKey).orElseThrow();
            if (!raced.requestFingerprint().equals(fingerprint)) {
                throw new HistoricalResearchException(409, "IDEMPOTENCY_CONFLICT", "idempotency key was reused with a different request");
            }
            return response(raced);
        }
        dispatchAfterCommit(job.id());
        return response(job);
    }

    @Transactional(readOnly = true)
    public HistoricalDatasetExportResponse status(MiAgentPrincipal agent, UUID id, String correlationId) {
        var job = jobs.findByIdAndAgentId(id, agent.agentId()).orElseThrow(() ->
                new HistoricalResearchException(404, "EXPORT_NOT_FOUND", "export job not found"));
        HistoricalDatasetExportRequest request = readRequest(job.requestJson());
        Ml01ExportSpec ml01 = readMl01ExportSpec(request.population());
        if (ml01 == null) {
            authorize(agent, job.scopeId(), "research:export", readPopulation(request.population()), request, correlationId);
        } else {
            authorize(agent, job.scopeId(), "research:export", ml01, request, correlationId);
        }
        return response(job);
    }

    private void execute(UUID id) {
        var job = jobs.findById(id).orElse(null);
        if (job == null || job.status() != HistoricalDatasetExportJobStatus.PENDING) return;
        Instant now = clock.instant();
        if (!job.expiresAt().isAfter(now)) {
            job.expire(now); jobs.save(job); return;
        }
        job.running(now); jobs.save(job);
        try {
            HistoricalDatasetExportRequest request = readRequest(job.requestJson());
            Ml01ExportSpec ml01 = readMl01ExportSpec(request.population());
            var decision = ml01 == null
                    ? authorization.authorize(new ResearchAuthorizationClient.AuthorizationRequest(
                    job.agentId(), job.credentialId(), job.credentialVersion(), job.scopeId(), null,
                    readPopulation(request.population()).marketIds(), job.correlationId(), "research:export",
                    readPopulation(request.population()).startCutoffInclusive(),
                    readPopulation(request.population()).endCutoffExclusive(), request.population().path("datasetProfile").asText("default"),
                    0, request.maxRows(), request.maxBytes(), request.maxDurationSeconds()))
                    : authorization.authorize(new ResearchAuthorizationClient.AuthorizationRequest(
                    job.agentId(), job.credentialId(), job.credentialVersion(), job.scopeId(), null, ml01.marketIds(),
                    job.correlationId(), "research:export", ml01.startCutoffInclusive(), ml01.endCutoffExclusive(),
                    ml01.datasetProfile(), 0, request.maxRows(), request.maxBytes(), request.maxDurationSeconds()));
            if (decision == null || !decision.allowed()) {
                job.expire(clock.instant()); jobs.save(job); return;
            }
            Path directory = outputRoot.resolve(job.id().toString()).normalize();
            if (!directory.startsWith(outputRoot)) throw new IllegalStateException("UNSAFE_OUTPUT_PATH");
            var started = clock.instant();
            int sampleCount;
            if (ml01 != null) {
                var prepared = prepareMl01Export(ml01, directory);
                Ml01HistDataPreparation.validatePublishedBundle(directory, ml01HistDataRoot);
                sampleCount = prepared.rows();
            } else {
                ResearchPopulationSpec population = readPopulation(request.population());
                var result = exporter.export(population, directory, request.maxRows(), request.maxBytes(),
                        () -> clock.instant().isBefore(job.expiresAt()));
                exporter.validatePublishedBundle(directory, population);
                sampleCount = result.sampleCount();
            }
            long bytes;
            try (var paths = Files.walk(directory)) {
                bytes = paths.filter(Files::isRegularFile).mapToLong(path -> {
                    try { return Files.size(path); } catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
                }).sum();
            }
            if (Duration.between(started, clock.instant()).toSeconds() > request.maxDurationSeconds()) {
                throw new IllegalStateException("EXPORT_DURATION_LIMIT");
            }
            if (sampleCount > request.maxRows()) throw new IllegalStateException("EXPORT_ROW_LIMIT");
            if (bytes > request.maxBytes()) throw new IllegalStateException("EXPORT_SIZE_LIMIT");
            Path manifest = directory.resolve(ResearchDatasetBundleExporter.MANIFEST_FILE);
            if (!Files.exists(manifest)) manifest = directory.resolve("dataset-manifest.json");
            String checksum = sha256(Files.readAllBytes(manifest));
            job.success(job.id().toString(), checksum, bytes, sampleCount, clock.instant());
            jobs.save(job);
        } catch (Exception failure) {
            boolean cleaned = deleteOutput(directoryFor(id));
            job.fail(cleaned ? classify(failure) : "EXPORT_CLEANUP_FAILED", clock.instant());
            jobs.save(job);
        }
    }

    Ml01HistDataPreparation.PreparationResult prepareMl01Export(Ml01ExportSpec ml01, Path directory)
            throws java.io.IOException {
        List<Ml01HistDataPreparation.ClosureEvidence> evidence = closureEvidenceSource
                .find(ml01.marketIds(), ml01.startCutoffInclusive(), ml01.endCutoffExclusive())
                .stream()
                .map(value -> HistoricalClosureEvidenceAdapter.toMl01(value, ml01.marketIds(),
                        ml01.startCutoffInclusive(), ml01.endCutoffExclusive()))
                .toList();
        return Ml01HistDataPreparation.prepare(ml01HistDataRoot, directory, ml01.partitions(),
                ml01.scope(), evidence);
    }

    private ResearchAuthorizationClient.AuthorizationResponse authorize(MiAgentPrincipal agent, UUID scopeId, String permission, ResearchPopulationSpec population,
                           HistoricalDatasetExportRequest request, String correlationId) {
        if (population.marketIds().isEmpty() || population.marketIds().size() > 2) {
            throw new HistoricalResearchException(400, "INVALID_POPULATION", "population market count is outside the supported scope");
        }
        return authorize(agent, scopeId, permission, population.marketIds(), population.startCutoffInclusive(),
                population.endCutoffExclusive(), request, correlationId);
    }

    private ResearchAuthorizationClient.AuthorizationResponse authorize(MiAgentPrincipal agent, UUID scopeId,
                                                                         String permission, Ml01ExportSpec population,
                                                                         HistoricalDatasetExportRequest request,
                                                                         String correlationId) {
        return authorize(agent, scopeId, permission, population.marketIds(), population.startCutoffInclusive(),
                population.endCutoffExclusive(), request, correlationId, population.datasetProfile());
    }

    private ResearchAuthorizationClient.AuthorizationResponse authorize(MiAgentPrincipal agent, UUID scopeId,
                                                                         String permission, Set<UUID> marketIds,
                                                                         Instant start, Instant end,
                                                                         HistoricalDatasetExportRequest request,
                                                                         String correlationId) {
        return authorize(agent, scopeId, permission, marketIds, start, end, request, correlationId,
                request.population().path("datasetProfile").asText("default"));
    }

    private ResearchAuthorizationClient.AuthorizationResponse authorize(MiAgentPrincipal agent, UUID scopeId,
                                                                         String permission, Set<UUID> marketIds,
                                                                         Instant start, Instant end,
                                                                         HistoricalDatasetExportRequest request,
                                                                         String correlationId, String datasetProfile) {
        var response = authorization.authorize(new ResearchAuthorizationClient.AuthorizationRequest(
                agent.agentId(), agent.credentialId(), agent.credentialVersion(), scopeId, null,
                marketIds, correlationId, permission, start, end, datasetProfile, 0, request.maxRows(),
                request.maxBytes(), request.maxDurationSeconds()));
        if (response == null || !response.allowed()) {
            throw new HistoricalResearchException(403, "RESEARCH_NOT_AUTHORIZED", "research export is not authorized");
        }
        return response;
    }

    Ml01ExportSpec readMl01ExportSpec(JsonNode root) {
        if (!"ml01-h1-histdata-v1".equals(root.path("datasetProfile").asText())) return null;
        try {
            Set<UUID> markets = new HashSet<>();
            root.path("marketIds").forEach(value -> markets.add(UUID.fromString(value.asText())));
            if (markets.size() != 2) throw new IllegalArgumentException("ML-01 requires two markets");
            if (!"H1".equals(root.path("timeframe").asText())
                    || !Ml01HistDataPreparation.RESEARCH_FEATURE_SCHEMA_VERSION
                    .equals(root.path("researchFeatureSchemaVersion").asText())
                    || !Ml01HistDataPreparation.RESEARCH_EXPORT_POLICY.equals(root.path("researchFeatureEvidencePolicy").asText())
                    || !Ml01HistDataPreparation.SOURCE_PROFILE.equals(root.path("sourceProfile").asText())
                    || !Ml01ExploratoryH1AdmissibilityProfile.V1.name().equals(root.path("admissibilityProfile").asText())
                    || !Ml01ExploratoryH1AdmissibilityProfile.V1.version().equals(root.path("admissibilityPolicyVersion").asText())
                    || !Ml01HistDataPreparation.OUTCOME_DEFINITION_VERSION.equals(root.path("outcomeDefinitionVersion").asText())
                    || !Ml01HistDataPreparation.CONTINUITY_SCOPE.equals(root.path("continuityScope").asText())
                    || !Ml01HistDataPreparation.SESSION_TRANSITION_STATUS.equals(root.path("sessionTransitionStatus").asText())
                    || !Ml01HistDataPreparation.SOURCE_CHECKSUM_ALGORITHM.equals(root.path("sourceChecksumAlgorithm").asText())
                    || !Ml01HistDataPreparation.SOURCE_CHECKSUM_SCOPE.equals(root.path("sourceChecksumScope").asText())
                    || !Ml01HistDataPreparation.NORMALIZATION_VERSION.equals(root.path("normalizationVersion").asText())) {
                throw new IllegalArgumentException("ML-01 research feature and source contract mismatch");
            }
            Set<Integer> horizons = new HashSet<>();
            root.path("horizons").forEach(value -> horizons.add(value.path("validH1Candles").asInt(-1)));
            if (!horizons.equals(Set.of(4, 12, 24))) {
                throw new IllegalArgumentException("ML-01 valid H1 horizons are required");
            }
            Instant start = Instant.parse(root.path("startCutoffInclusive").asText());
            Instant end = Instant.parse(root.path("endCutoffExclusive").asText());
            String train = root.path("trainEndExclusive").isMissingNode() ? null : root.path("trainEndExclusive").asText(null);
            String validation = root.path("validationEndExclusive").isMissingNode() ? null : root.path("validationEndExclusive").asText(null);
            String test = root.path("testEndExclusive").isMissingNode() ? null : root.path("testEndExclusive").asText(null);
            String embargo = root.path("embargo").isMissingNode() ? null : root.path("embargo").asText(null);
            if (train == null && validation == null && test == null && embargo == null) {
                return new Ml01ExportSpec(markets, start, end, root.path("datasetProfile").asText(),
                        new Ml01HistDataPreparation.PreparationScope(markets, start, end),
                        Ml01HistDataPreparation.PartitionConfiguration.requiresApproval());
            }
            return new Ml01ExportSpec(markets, start, end, root.path("datasetProfile").asText(),
                    new Ml01HistDataPreparation.PreparationScope(markets, start, end),
                    new Ml01HistDataPreparation.PartitionConfiguration(Instant.parse(train), Instant.parse(validation),
                            Instant.parse(test), Duration.parse(embargo)));
        } catch (Exception exception) {
            throw new HistoricalResearchException(400, "INVALID_ML01_POPULATION", "invalid ML-01.1 population specification");
        }
    }

    record Ml01ExportSpec(Set<UUID> marketIds, Instant startCutoffInclusive, Instant endCutoffExclusive,
                                  String datasetProfile, Ml01HistDataPreparation.PreparationScope scope,
                                  Ml01HistDataPreparation.PartitionConfiguration partitions) {
    }

    private void dispatchAfterCommit(UUID id) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            executor.execute(() -> execute(id));
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { executor.execute(() -> execute(id)); }
        });
    }

    private HistoricalDatasetExportRequest readRequest(String json) {
        try { return mapper.readValue(json, HistoricalDatasetExportRequest.class); }
        catch (Exception exception) { throw new HistoricalResearchException(400, "INVALID_EXPORT_REQUEST", "invalid export request"); }
    }

    private ResearchPopulationSpec readPopulation(JsonNode root) {
        try {
            Set<UUID> markets = new HashSet<>(); root.path("marketIds").forEach(v -> markets.add(UUID.fromString(v.asText())));
            Set<OutcomeHorizon> horizons = new HashSet<>(); root.path("horizons").forEach(v -> horizons.add(new OutcomeHorizon(Duration.parse(v.asText()))));
            Set<FeatureMissingness> missingness = new HashSet<>(); root.path("acceptedFeatureMissingness").forEach(v -> missingness.add(FeatureMissingness.valueOf(v.asText())));
            Map<ResearchOutcomeKey, String> selected = new HashMap<>(); root.path("selectedOutcomeFingerprints").forEach(v -> selected.put(
                    new ResearchOutcomeKey(UUID.fromString(v.path("marketId").asText()), Instant.parse(v.path("evaluationCutoff").asText()), new OutcomeHorizon(Duration.parse(v.path("horizon").asText()))), v.path("outcomeFingerprint").asText()));
            Map<ResearchFeatureSnapshotKey, String> selectedSnapshots = new HashMap<>(); root.path("selectedFeatureSnapshotFingerprints").forEach(v -> selectedSnapshots.put(
                    new ResearchFeatureSnapshotKey(UUID.fromString(v.path("marketId").asText()), Instant.parse(v.path("evaluationCutoff").asText())), v.path("snapshotFingerprint").asText()));
            JsonNode requirements = root.get("featureRequirements");
            if (requirements == null || !requirements.isObject()) throw new IllegalArgumentException("featureRequirements is required");
            List<String> requiredFeatures = new ArrayList<>(); requirements.path("requiredFeatureNames").forEach(v -> requiredFeatures.add(v.asText()));
            Set<FeatureMissingness> requiredMissingness = new HashSet<>(); requirements.path("acceptedMissingness").forEach(v -> requiredMissingness.add(FeatureMissingness.valueOf(v.asText())));
            return new ResearchPopulationSpec(root.path("populationPolicyVersion").asText(), markets,
                    Instant.parse(root.path("startCutoffInclusive").asText()), Instant.parse(root.path("endCutoffExclusive").asText()),
                    root.path("featureProjectionVersion").asText(), EvidenceAcquisitionPolicy.valueOf(root.path("featureEvidencePolicy").asText()),
                    root.path("outcomeDefinitionVersion").asText(), horizons, ResearchSamplingCadence.valueOf(root.path("samplingCadence").asText()),
                    missingness, selectedSnapshots, ResearchFeatureRequirements.fromNames(requirements.path("version").asText(), requiredFeatures, requiredMissingness), selected);
        } catch (Exception exception) {
            throw new HistoricalResearchException(400, "INVALID_POPULATION", "invalid population specification");
        }
    }

    private String fingerprint(UUID scopeId, MiAgentPrincipal agent,
                               ResearchAuthorizationClient.AuthorizationResponse decision,
                               HistoricalDatasetExportRequest request) {
        try {
            ObjectNode material = mapper.createObjectNode();
            material.put("scopeId", scopeId.toString());
            material.put("agentId", agent.agentId().toString());
            material.put("grantId", decision.grantId().toString());
            material.put("grantVersion", decision.grantVersion());
            material.put("credentialId", agent.credentialId().toString());
            material.put("credentialVersion", agent.credentialVersion());
            material.set("request", canonical(mapper.valueToTree(request)));
            return sha256(mapper.writeValueAsBytes(material));
        }
        catch (Exception exception) {
            throw new HistoricalResearchException(400, "INVALID_EXPORT_REQUEST", "invalid export request");
        }
    }

    private JsonNode canonical(JsonNode value) {
        if (value.isObject()) {
            ObjectNode result = mapper.createObjectNode();
            var names = new ArrayList<String>();
            value.fieldNames().forEachRemaining(names::add);
            names.sort(String::compareTo);
            names.forEach(name -> result.set(name, canonical(value.get(name))));
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = mapper.createArrayNode();
            value.forEach(item -> result.add(canonical(item)));
            return result;
        }
        return value;
    }

    private Path directoryFor(UUID id) { return outputRoot.resolve(id.toString()).normalize(); }

    private boolean deleteOutput(Path directory) {
        try {
            if (!directory.startsWith(outputRoot)) return false;
            if (!Files.exists(directory)) return true;
            try (var paths = Files.walk(directory)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (java.io.IOException ignored) { }
                });
            }
            return !Files.exists(directory);
        } catch (java.io.IOException ignored) { }
        return false;
    }

    private String serialize(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception exception) {
            throw new HistoricalResearchException(400, "INVALID_EXPORT_REQUEST", "invalid export request");
        }
    }

    private String sha256(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private String classify(Exception failure) {
        if (failure instanceof HistoricalResearchException historical) return historical.code();
        String message = failure.getMessage();
        if (message != null) {
            if (message.contains("row limit")) return "EXPORT_ROW_LIMIT";
            if (message.contains("byte limit") || message.contains("output exceeds")) return "EXPORT_SIZE_LIMIT";
            if (message.contains("limit exceeded")) return "EXPORT_DURATION_LIMIT";
        }
        return "EXPORT_FAILED";
    }

    private HistoricalDatasetExportResponse response(HistoricalDatasetExportJobEntity job) {
        return new HistoricalDatasetExportResponse(job.id(), job.status(), job.artifactId(), job.artifactChecksum(),
                job.artifactBytes(), job.sampleCount(), job.contractValidationStatus(),
                job.experimentEligibilityStatus(), job.failureCode(), job.createdAt(), job.startedAt(),
                job.completedAt(), job.expiresAt(), job.correlationId());
    }
}
