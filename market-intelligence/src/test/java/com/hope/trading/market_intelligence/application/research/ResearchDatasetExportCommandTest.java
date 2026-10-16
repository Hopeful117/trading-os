package com.hope.trading.market_intelligence.application.research;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.market_intelligence.domain.research.ResearchPopulationSpec;
import com.hope.trading.market_intelligence.domain.outcome.OutcomeHorizon;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;

class ResearchDatasetExportCommandTest {
    private static final UUID MARKET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @TempDir
    Path tempDir;

    @Test
    void readsPopulationSpec() throws Exception {
        Path spec = tempDir.resolve("population.json");
        Files.writeString(spec, """
                {
                  "populationPolicyVersion": "population-v1",
                  "marketIds": ["11111111-1111-1111-1111-111111111111"],
                  "startCutoffInclusive": "2026-10-01T00:00:00Z",
                  "endCutoffExclusive": "2026-10-02T00:00:00Z",
                  "featureProjectionVersion": "feature-projection-v1",
                  "featureEvidencePolicy": "STRICT_OBSERVED",
                  "outcomeDefinitionVersion": "outcome-v1",
                  "horizons": ["PT1H"],
                  "samplingCadence": "ONE_PER_FEATURE_SNAPSHOT",
                  "acceptedFeatureMissingness": ["PRESENT"],
                  "selectedOutcomeFingerprints": []
                }
                """);

        ResearchDatasetExportCommand command = new ResearchDatasetExportCommand(
                new ObjectMapper(), mock(ResearchDatasetExportService.class),
                mock(ConfigurableApplicationContext.class));

        Method readPopulation = ResearchDatasetExportCommand.class
                .getDeclaredMethod("readPopulation", Path.class);
        readPopulation.setAccessible(true);
        ResearchPopulationSpec population = (ResearchPopulationSpec) readPopulation.invoke(command, spec);

        assertThat(population.marketIds()).containsExactly(MARKET_ID);
        assertThat(population.horizons()).extracting(OutcomeHorizon::value)
                .containsExactly(Duration.ofHours(1));
        assertThat(population.startCutoffInclusive())
                .isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
    }

    @Test
    void rejectsMissingRequiredOption() {
        ResearchDatasetExportCommand command = new ResearchDatasetExportCommand(
                new ObjectMapper(), mock(ResearchDatasetExportService.class),
                mock(ConfigurableApplicationContext.class));

        DefaultApplicationArguments arguments = new DefaultApplicationArguments();
        assertThatThrownBy(() -> command.run(arguments))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("research.dataset.export.spec");
    }

    @Test
    void rejectsPopulationSpecWithoutMarkets() throws Exception {
        Path spec = tempDir.resolve("invalid-population.json");
        Files.writeString(spec, "{}");
        ResearchDatasetExportCommand command = new ResearchDatasetExportCommand(
                new ObjectMapper(), mock(ResearchDatasetExportService.class),
                mock(ConfigurableApplicationContext.class));
        Method readPopulation = ResearchDatasetExportCommand.class
                .getDeclaredMethod("readPopulation", Path.class);
        readPopulation.setAccessible(true);

        assertThatThrownBy(() -> readPopulation.invoke(command, spec))
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exportsPopulationAndRequestsApplicationExit() throws Exception {
        Path spec = tempDir.resolve("population.json");
        Files.writeString(spec, """
                {
                  "populationPolicyVersion": "population-v1",
                  "marketIds": ["11111111-1111-1111-1111-111111111111"],
                  "startCutoffInclusive": "2026-10-01T00:00:00Z",
                  "endCutoffExclusive": "2026-10-02T00:00:00Z",
                  "featureProjectionVersion": "feature-projection-v1",
                  "featureEvidencePolicy": "STRICT_OBSERVED",
                  "outcomeDefinitionVersion": "outcome-v1",
                  "horizons": ["PT1H"],
                  "samplingCadence": "ONE_PER_FEATURE_SNAPSHOT",
                  "acceptedFeatureMissingness": ["PRESENT"],
                  "selectedOutcomeFingerprints": []
                }
                """);
        ResearchDatasetExportService service = mock(ResearchDatasetExportService.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        ResearchDatasetExportCommand command = new ResearchDatasetExportCommand(
                new ObjectMapper(), service, context);
        DefaultApplicationArguments arguments = new DefaultApplicationArguments(
                "--research.dataset.export.spec=" + spec,
                "--research.dataset.export.output=" + tempDir.resolve("output"));

        try (var ignored = org.mockito.Mockito.mockStatic(SpringApplication.class)) {
            command.run(arguments);
        }

        verify(service).export(org.mockito.ArgumentMatchers.any(),
                eq(tempDir.resolve("output")));
    }
}
