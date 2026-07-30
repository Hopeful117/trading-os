package com.hope.trading.market_intelligence.architecture;

import com.hope.trading.market_intelligence.application.tradeplan.TradePlanningEngine;
import com.hope.trading.market_intelligence.domain.tradeplan.*;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static org.assertj.core.api.Assertions.assertThat;

class TradePlanArchitectureTest {
    private static final String ROOT = "com.hope.trading.market_intelligence";
    private final JavaClasses classes = new ClassFileImporter()
            .importPath(Path.of("target/classes"));

    @Test
    void tradePlanDomainHasNoInfrastructureApplicationBrokerOrRiskDependency() {
        noClasses().that().resideInAPackage(ROOT + ".domain.tradeplan..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        ROOT + ".application..", ROOT + ".adapter..",
                        "..broker..", "..risk..", "jakarta.persistence..")
                .check(classes);
    }

    @Test
    void onlyEngineUsesInternalBuilderAndBuilderIsNotPublic() {
        noClasses().that().doNotHaveSimpleName("TradePlanningEngine")
                .and().doNotHaveSimpleName("TradePlanBuilder")
                .should().dependOnClassesThat().haveSimpleName("TradePlanBuilder")
                .check(classes);
        assertThat(Modifier.isPublic(classFor(
                ROOT + ".application.tradeplan.TradePlanBuilder").getModifiers())).isFalse();
    }

    @Test
    void aggregateIsImmutableAndContainsNoBrokerExecutionPositionOrPnlState() {
        assertThat(TradePlan.class.getDeclaredFields())
                .allMatch(field -> Modifier.isPrivate(field.getModifiers())
                        && Modifier.isFinal(field.getModifiers()));
        assertThat(TradePlan.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("brokerOrder", "executionReport", "position", "realizedPnl");
        noClasses().that().resideInAPackage(ROOT + ".application.tradeplan..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        ROOT + ".adapter..", "..broker..")
                .check(classes);
    }
    private static Class<?> classFor(String name) {
        try { return Class.forName(name); }
        catch (ClassNotFoundException exception) { throw new AssertionError(exception); }
    }
}
