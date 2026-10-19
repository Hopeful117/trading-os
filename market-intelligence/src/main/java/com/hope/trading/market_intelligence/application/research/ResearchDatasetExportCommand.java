package com.hope.trading.market_intelligence.application.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.feature.FeatureMissingness;
import com.hope.trading.market_intelligence.domain.outcome.OutcomeHorizon;
import com.hope.trading.market_intelligence.domain.research.ResearchOutcomeKey;
import com.hope.trading.market_intelligence.domain.research.ResearchFeatureRequirements;
import com.hope.trading.market_intelligence.domain.research.ResearchFeatureSnapshotKey;
import com.hope.trading.market_intelligence.domain.research.ResearchPopulationSpec;
import com.hope.trading.market_intelligence.domain.research.ResearchSamplingCadence;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "research.dataset.export.enabled", havingValue = "true")
public final class ResearchDatasetExportCommand implements ApplicationRunner {
    private final ObjectMapper mapper;
    private final ResearchDatasetExportService service;
    private final ConfigurableApplicationContext context;

    public ResearchDatasetExportCommand(
            ObjectMapper mapper,
            ResearchDatasetExportService service,
            ConfigurableApplicationContext context
    ) {
        this.mapper = mapper;
        this.service = service;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments arguments) throws Exception {
        String specPath = requiredOption(arguments, "research.dataset.export.spec");
        String outputPath = requiredOption(arguments, "research.dataset.export.output");
        ResearchPopulationSpec population = readPopulation(Path.of(specPath));
        service.export(population, Path.of(outputPath));
        SpringApplication.exit(context, () -> 0);
    }

    private ResearchPopulationSpec readPopulation(Path path) throws IOException {
        JsonNode root = mapper.readTree(path.toFile());
        JsonNode marketIds = requiredArray(root, "marketIds");
        Set<UUID> markets = new HashSet<>();
        marketIds.forEach(value -> markets.add(UUID.fromString(value.asText())));
        Set<OutcomeHorizon> horizons = new HashSet<>();
        root.path("horizons").forEach(value -> horizons.add(new OutcomeHorizon(Duration.parse(value.asText()))));
        Set<FeatureMissingness> missingness = new HashSet<>();
        root.path("acceptedFeatureMissingness").forEach(value -> missingness.add(FeatureMissingness.valueOf(value.asText())));
        Map<ResearchOutcomeKey, String> selected = new HashMap<>();
        root.path("selectedOutcomeFingerprints").forEach(value -> {
            ResearchOutcomeKey key = new ResearchOutcomeKey(UUID.fromString(value.path("marketId").asText()),
                    Instant.parse(value.path("evaluationCutoff").asText()),
                    new OutcomeHorizon(Duration.parse(value.path("horizon").asText())));
            if (selected.containsKey(key)) {
                throw new IllegalArgumentException("duplicate selected outcome key: " + key);
            }
            selected.put(key, value.path("outcomeFingerprint").asText());
        });
        Map<ResearchFeatureSnapshotKey, String> selectedSnapshots = new HashMap<>();
        requiredArray(root, "selectedFeatureSnapshotFingerprints").forEach(value -> {
            ResearchFeatureSnapshotKey key = new ResearchFeatureSnapshotKey(
                    UUID.fromString(value.path("marketId").asText()),
                    Instant.parse(value.path("evaluationCutoff").asText()));
            if (selectedSnapshots.containsKey(key)) {
                throw new IllegalArgumentException("duplicate selected feature snapshot key: " + key);
            }
            selectedSnapshots.put(key, value.path("snapshotFingerprint").asText());
        });
        JsonNode requirements = root.get("featureRequirements");
        if (requirements == null || !requirements.isObject()) {
            throw new IllegalArgumentException("required object field: featureRequirements");
        }
        List<String> requiredFeatures = new ArrayList<>();
        requirements.path("requiredFeatureNames").forEach(value -> requiredFeatures.add(value.asText()));
        Set<FeatureMissingness> requiredMissingness = new HashSet<>();
        requirements.path("acceptedMissingness").forEach(value -> requiredMissingness.add(FeatureMissingness.valueOf(value.asText())));
        return new ResearchPopulationSpec(
                root.path("populationPolicyVersion").asText(), markets,
                Instant.parse(root.path("startCutoffInclusive").asText()),
                Instant.parse(root.path("endCutoffExclusive").asText()),
                root.path("featureProjectionVersion").asText(),
                EvidenceAcquisitionPolicy.valueOf(root.path("featureEvidencePolicy").asText()),
                root.path("outcomeDefinitionVersion").asText(), horizons,
                ResearchSamplingCadence.valueOf(root.path("samplingCadence").asText()),
                 missingness, selectedSnapshots,
                 ResearchFeatureRequirements.fromNames(requirements.path("version").asText(), requiredFeatures, requiredMissingness),
                 selected);
    }

    private JsonNode requiredArray(JsonNode root, String name) {
        JsonNode value = root.get(name);
        if (value == null || !value.isArray()) {
            throw new IllegalArgumentException("required array field: " + name);
        }
        return value;
    }

    private String requiredOption(ApplicationArguments arguments, String name) {
        List<String> values = arguments.getOptionValues(name);
        if (values == null || values.size() != 1 || values.getFirst().isBlank()) {
            throw new IllegalArgumentException("required option: --" + name + "=<value>");
        }
        return values.getFirst();
    }
}
