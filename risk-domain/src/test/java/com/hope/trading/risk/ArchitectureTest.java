package com.hope.trading.risk;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArchitectureTest {
    @Test void productionDomainContainsNoFrameworkInfrastructureOrAiImports() throws IOException {
        Path sources = Path.of("src/main/java");
        try (var files = Files.walk(sources)) {
            String all = files.filter(p -> p.toString().endsWith(".java"))
                    .map(this::read).reduce("", String::concat);
            assertFalse(all.contains("org.springframework"));
            assertFalse(all.contains("jakarta.persistence"));
            assertFalse(all.contains("com.hope.trading.market_intelligence"));
            assertFalse(all.contains("com.hope.trading.trading_core"));
        }
    }

    @Test void concreteRulesContainNoFinancialArithmetic() throws IOException {
        Path rules = Path.of("src/main/java/com/hope/trading/risk/rule");
        for (String file : List.of("MaximumPositionRiskRule.java",
                "MaximumExposureRule.java", "DailyDrawdownRule.java")) {
            String source = Files.readString(rules.resolve(file));
            assertFalse(source.contains(".divide("));
            assertFalse(source.contains(".multiply("));
            assertFalse(source.contains(".subtract("));
            assertFalse(source.contains("expectedLossAtStop"));
            assertFalse(source.contains("projectedExposure().amount"));
        }
    }
    private String read(Path path) {
        try { return Files.readString(path); }
        catch (IOException e) { throw new IllegalStateException(e); }
    }
}
