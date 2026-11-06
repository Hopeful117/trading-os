package com.hope.trading.market_intelligence.application.research;

import com.hope.trading.market_intelligence.application.port.FeatureSnapshotRepository;
import com.hope.trading.market_intelligence.application.port.AnalysisExecutionRepository;
import com.hope.trading.market_intelligence.application.port.ObservedMarketOutcomeRepository;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcome;
import com.hope.trading.market_intelligence.domain.research.ResearchOutcomeKey;
import com.hope.trading.market_intelligence.domain.research.ResearchPopulationSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ResearchDatasetExportService {
    private final FeatureSnapshotRepository snapshots;
    private final ObservedMarketOutcomeRepository outcomes;
    private final AnalysisExecutionRepository executions;
    private final ResearchDatasetBuilder builder;
    private final ResearchDatasetBundleExporter exporter;

    public ResearchDatasetExportService(
            FeatureSnapshotRepository snapshots,
            ObservedMarketOutcomeRepository outcomes,
            AnalysisExecutionRepository executions,
            ObjectMapper mapper
    ) {
        this.snapshots = snapshots;
        this.outcomes = outcomes;
        this.executions = executions;
        this.builder = new ResearchDatasetBuilder(java.time.Clock.systemUTC());
        this.exporter = new ResearchDatasetBundleExporter(mapper, executions);
    }

    @Transactional(readOnly = true)
    public ExportResult export(ResearchPopulationSpec population, Path outputDirectory) {
        Objects.requireNonNull(population, "population");
        var domainSnapshots = snapshots.findByPopulation(
                population.marketIds(),
                population.startCutoffInclusive(), population.endCutoffExclusive(),
                population.featureProjectionVersion(), population.featureEvidencePolicy().name());
        var selectedOutcomes = population.selectedOutcomeFingerprints().entrySet().stream()
                .map(entry -> resolveSelectedOutcome(population, entry.getKey(), entry.getValue()))
                .toList();
        DatasetBuildResult dataset = builder.build(population, domainSnapshots, selectedOutcomes,
                evidenceKnownAt(domainSnapshots), true);
        exporter.export(dataset, outputDirectory);
        return new ExportResult(dataset.manifest().sampleCount(), dataset.manifest().eligibleCount(),
                dataset.manifest().excludedCount());
    }

    private Map<String, Instant> evidenceKnownAt(
            java.util.List<com.hope.trading.market_intelligence.domain.feature.FeatureSnapshot> snapshots) {
        Map<UUID, com.hope.trading.market_intelligence.domain.execution.AnalysisExecution> byExecution =
                executions.findAllById(snapshots.stream().map(value -> value.analysisExecutionId()).collect(Collectors.toSet()))
                        .stream().collect(Collectors.toMap(value -> value.executionId(), value -> value));
        return snapshots.stream()
                .map(value -> Map.entry(value.fingerprint(), knownAt(value, byExecution.get(value.analysisExecutionId()))))
                .filter(value -> value.getValue().isPresent())
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, value -> value.getValue().orElseThrow()));
    }

    private Optional<Instant> knownAt(
            com.hope.trading.market_intelligence.domain.feature.FeatureSnapshot snapshot,
            com.hope.trading.market_intelligence.domain.execution.AnalysisExecution execution) {
        var result = execution == null ? null : execution.result().orElse(null);
        if (result == null || result.contextSections().isEmpty()) {
            return Optional.empty();
        }
        Instant knownAt = result.contextSections().stream()
                .map(value -> value.knownAt() == null ? value.fetchedAt() : value.knownAt())
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);
        if (knownAt == null) {
            return Optional.empty();
        }
        if (knownAt.isAfter(snapshot.evaluationCutoff())) {
            return Optional.empty();
        }
        return Optional.of(knownAt);
    }

    private ObservedMarketOutcome resolveSelectedOutcome(
            ResearchPopulationSpec population, ResearchOutcomeKey key, String fingerprint) {
        ObservedMarketOutcome outcome = outcomes.findByFingerprint(fingerprint)
                .orElseThrow(() -> new IllegalStateException(
                        "selected outcome fingerprint is missing: " + fingerprint));
        if (!outcome.marketId().equals(key.marketId())
                || !outcome.evaluationCutoff().equals(key.evaluationCutoff())
                || !outcome.horizon().equals(key.horizon())
                || !outcome.outcomeDefinitionVersion().equals(population.outcomeDefinitionVersion())) {
            throw new IllegalStateException("selected outcome identity does not match population key: " + key);
        }
        return outcome;
    }

    public record ExportResult(int sampleCount, int eligibleCount, int excludedCount) { }
}
