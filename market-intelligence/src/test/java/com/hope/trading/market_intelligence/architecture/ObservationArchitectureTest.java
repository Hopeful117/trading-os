package com.hope.trading.market_intelligence.architecture;

import com.hope.trading.market_intelligence.application.observation.ObservationBuilder;
import com.hope.trading.market_intelligence.domain.observation.*;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.nio.file.Path;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static org.assertj.core.api.Assertions.assertThat;

class ObservationArchitectureTest {
    private static final String ROOT =
            "com.hope.trading.market_intelligence";
    private final JavaClasses classes = new ClassFileImporter()
            .importPath(Path.of("target/classes"));

    @Test
    void capabilitiesNeverDependOnObservationModel() {
        noClasses().that().resideInAnyPackage(
                        ROOT + ".domain.capability..",
                        ROOT + ".application.capability..")
                .should().dependOnClassesThat().resideInAPackage(
                        ROOT + ".domain.observation..")
                .check(classes);
    }

    @Test
    void aiCodeCannotCreateOrModifyObservations() {
        noClasses().that().resideInAnyPackage(
                        ROOT + ".adapter.ai..", ROOT + ".application.capability..")
                .should().dependOnClassesThat().areAssignableTo(ObservationBuilder.class)
                .orShould().dependOnClassesThat().areAssignableTo(ObservationFactory.class)
                .check(classes);
    }

    @Test
    void onlyFactoryInvokesObservationConstructorAndOnlyBuilderUsesFactoryOperations() {
        noClasses().that().areNotAssignableTo(ObservationFactory.class)
                .should().callConstructor(Observation.class,
                        java.util.UUID.class, java.util.UUID.class, long.class, String.class,
                        ObservationType.class, ObservationStatus.class, String.class, String.class,
                        java.util.Set.class, String.class, java.time.Instant.class,
                        java.time.Instant.class, java.time.Instant.class, java.util.UUID.class,
                        java.util.UUID.class, String.class, java.util.List.class,
                        ObservationConfidence.class)
                .check(classes);
        noClasses().that().areNotAssignableTo(ObservationBuilder.class)
                .and().areNotAssignableTo(
                        com.hope.trading.market_intelligence.config.ObservationConfiguration.class)
                .should().dependOnClassesThat().areAssignableTo(ObservationFactory.class)
                .check(classes);
    }

    @Test
    void observationEvidenceAndConfidenceAreImmutable() {
        assertThat(Observation.class.getDeclaredFields())
                .allMatch(field -> Modifier.isPrivate(field.getModifiers())
                        && Modifier.isFinal(field.getModifiers()));
        assertThat(ObservationEvidence.class.isRecord()).isTrue();
        assertThat(ObservationConfidence.class.isRecord()).isTrue();
        fields().that().areDeclaredInClassesThat().areAssignableTo(Observation.class)
                .should().beFinal().check(classes);
    }
}
