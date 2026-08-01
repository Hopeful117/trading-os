package com.hope.trading.trading_core.risk.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RiskEvaluationArchitectureTest {
    @Test
    void riskEvaluationHasNoExecutionCoupling() throws Exception {
        Path root = Path.of("src/main/java/com/hope/trading/trading_core/risk");
        try (var files = Files.walk(root)) {
            assertThat(files.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> read(path)).filter(text -> text.contains("trading_core.execution")))
                    .isEmpty();
        }
    }

    private String read(Path path) {
        try { return Files.readString(path); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }
}
