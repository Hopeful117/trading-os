package com.hope.trading.market_intelligence.architecture;

import com.hope.trading.market_intelligence.domain.capability.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.*;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityArchitectureTest {
    @Test
    void capabilityContractExposesNoRepositoryOrEngine() {
        assertThat(Capability.class.getDeclaredMethods())
                .flatExtracting(method -> java.util.List.of(
                        method.getReturnType(),
                        method.getParameterTypes().length == 0
                                ? Void.class : method.getParameterTypes()[0]))
                .noneMatch(type -> type.getSimpleName().contains("Repository")
                        || type.getSimpleName().contains("ExecutionEngine"));
        assertThat(CapabilityContext.class.getRecordComponents())
                .noneMatch(component ->
                        component.getType().getSimpleName().contains("Repository")
                                || component.getType().getSimpleName().contains("Engine"));
    }

    @Test
    void domainSourcesDoNotDependOnApplicationOrAdapters() throws IOException {
        Path domain = Path.of(
                "src/main/java/com/hope/trading/market_intelligence/domain");
        try (var files = Files.walk(domain)) {
            assertThat(files.filter(path -> path.toString().endsWith(".java")))
                    .allMatch(path -> {
                        try {
                            String source = Files.readString(path);
                            return !source.contains(
                                    "com.hope.trading.market_intelligence.application")
                                    && !source.contains(
                                    "com.hope.trading.market_intelligence.adapter");
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    });
        }
    }
}
