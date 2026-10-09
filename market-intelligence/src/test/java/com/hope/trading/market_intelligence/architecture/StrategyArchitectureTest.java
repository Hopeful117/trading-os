package com.hope.trading.market_intelligence.architecture;

import com.hope.trading.market_intelligence.strategy.application.StrategyEvaluator;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluationContext;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class StrategyArchitectureTest {
    private static final String ROOT = "com.hope.trading.market_intelligence";
    private final JavaClasses classes = new ClassFileImporter()
            .importPath(Path.of("target/classes"));

    @Test
    void evaluatorsRemainInfrastructureFree() {
        noClasses().that().implement(StrategyEvaluator.class)
                .should().dependOnClassesThat().resideInAnyPackage(
                        ROOT + ".adapter..",
                        ROOT + ".application.pipeline..",
                        ROOT + ".adapter.web..",
                        "jakarta.persistence..",
                        "org.springframework.data..",
                        "org.springframework.web..",
                        "org.springframework.cloud.openfeign..")
                .check(classes);
    }

    @Test
    void strategyContextRemainsInfrastructureFree() {
        noClasses().that().resideInAnyPackage(ROOT + ".strategy.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        ROOT + ".adapter..",
                        ROOT + ".application.pipeline..",
                        "jakarta.persistence..",
                        "org.springframework..")
                .check(classes);
    }
}
