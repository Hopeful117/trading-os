package com.hope.trading.market_intelligence.architecture;

import com.hope.trading.market_intelligence.application.opportunity.*;
import com.hope.trading.market_intelligence.domain.opportunity.*;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static org.assertj.core.api.Assertions.assertThat;

class OpportunityArchitectureTest {
    private static final String ROOT = "com.hope.trading.market_intelligence";
    private final JavaClasses classes = new ClassFileImporter()
            .importPath(Path.of("target/classes"));

    @Test
    void domainHasNoApplicationAdapterOrPersistenceDependencies() {
        noClasses().that().resideInAPackage(ROOT + ".domain.opportunity..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        ROOT + ".application..", ROOT + ".adapter..", "jakarta.persistence..")
                .check(classes);
    }

    @Test
    void onlyEngineMayInvokeInternalBuilderAndRegistryCannotCreate() {
        noClasses().that().doNotHaveSimpleName("OpportunityEngine")
                .and().doNotHaveSimpleName("OpportunityBuilder")
                .should().dependOnClassesThat().haveSimpleName("OpportunityBuilder")
                .check(classes);
        noClasses().that().haveSimpleNameEndingWith("OpportunityRegistry")
                .should().dependOnClassesThat().areAssignableTo(OpportunityFactory.class)
                .check(classes);
        assertThat(Modifier.isPublic(classFor(
                ROOT + ".application.opportunity.OpportunityBuilder").getModifiers())).isFalse();
    }

    @Test
    void aggregateIsImmutableAndContainsNeitherUserNorExecutionFields() {
        assertThat(TradingOpportunity.class.getDeclaredFields())
                .allMatch(field -> Modifier.isPrivate(field.getModifiers())
                        && Modifier.isFinal(field.getModifiers()));
        assertThat(TradingOpportunity.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain(
                        "userId", "favorite", "hidden", "personalNotes", "entry",
                        "stopLoss", "takeProfit", "positionSize", "executionId",
                        "brokerAccountId");
        assertThat(TradingOpportunity.class.getDeclaredFields())
                .noneMatch(field -> Set.of(
                                UserOpportunity.class,
                                com.hope.trading.market_intelligence.domain.capability
                                        .CapabilityExecution.class)
                        .contains(field.getType()));
    }

    private static Class<?> classFor(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException exception) {
            throw new AssertionError(exception);
        }
    }
}
