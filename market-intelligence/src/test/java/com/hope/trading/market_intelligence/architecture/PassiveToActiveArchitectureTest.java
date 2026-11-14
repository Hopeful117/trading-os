package com.hope.trading.market_intelligence.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Story 0243 safety boundaries for the passive-to-active transition. */
class PassiveToActiveArchitectureTest {
    private static final String ROOT = "com.hope.trading.market_intelligence";
    private final JavaClasses classes = new ClassFileImporter().importPath(Path.of("target/classes"));

    @Test
    void passiveScanningNeverCreatesActiveScans() {
        noClasses().that().resideInAPackage(ROOT + ".application.passive..")
                .should().dependOnClassesThat().haveSimpleName("ActiveScanApplicationService")
                .orShould().dependOnClassesThat().haveSimpleName("ActiveScanCreationTransaction")
                .orShould().dependOnClassesThat().haveSimpleName("PassiveCandidateScanValidator")
                .check(classes);
    }

    @Test
    void candidateValidationHasNoTradingAuthorityDependencies() {
        noClasses().that().haveSimpleNameStartingWith("PassiveCandidate")
                .should().dependOnClassesThat().resideInAnyPackage(
                        ROOT + ".application.tradeplan..", ROOT + ".domain.tradeplan..",
                        ROOT + ".adapter.broker..", ROOT + ".adapter.tradingcore..", ROOT + ".adapter.ai..")
                .orShould().dependOnClassesThat().haveSimpleNameContaining("Risk")
                .orShould().dependOnClassesThat().haveSimpleNameContaining("TradePlan")
                .check(classes);
    }
}
