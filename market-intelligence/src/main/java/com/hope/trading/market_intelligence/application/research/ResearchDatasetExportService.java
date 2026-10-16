package com.hope.trading.market_intelligence.application.research;

import com.hope.trading.market_intelligence.application.port.FeatureSnapshotRepository;
import com.hope.trading.market_intelligence.application.port.ObservedMarketOutcomeRepository;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcome;
import com.hope.trading.market_intelligence.domain.research.ResearchOutcomeKey;
import com.hope.trading.market_intelligence.domain.research.ResearchPopulationSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.Objects;

@Service
public class ResearchDatasetExportService {
    private final FeatureSnapshotRepository snapshots;
    private final ObservedMarketOutcomeRepository outcomes;
    private final ResearchDatasetBuilder builder;
    private final ResearchDatasetBundleExporter exporter;

    public ResearchDatasetExportService(
            FeatureSnapshotRepository snapshots,
            ObservedMarketOutcomeRepository outcomes,
            ObjectMapper mapper
    ) {
        this.snapshots = snapshots;
        this.outcomes = outcomes;
        this.builder = new ResearchDatasetBuilder(java.time.Clock.systemUTC());
        this.exporter = new ResearchDatasetBundleExporter(mapper);
    }

    @Transactional(readOnly = true)
    public void export(ResearchPopulationSpec population, Path outputDirectory) {
        Objects.requireNonNull(population, "population");
        var domainSnapshots = snapshots.findByPopulation(
                population.marketIds(),
                population.startCutoffInclusive(), population.endCutoffExclusive(),
                population.featureProjectionVersion(), population.featureEvidencePolicy().name());
        var selectedOutcomes = population.selectedOutcomeFingerprints().entrySet().stream()
                .map(entry -> resolveSelectedOutcome(population, entry.getKey(), entry.getValue()))
                .toList();
        exporter.export(builder.build(population, domainSnapshots, selectedOutcomes), outputDirectory);
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
}
