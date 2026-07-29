package com.hope.trading.trading_core.brokeraccount.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class BrokerAccountArchitectureTest {
    @Test
    void tradingCoreBrokerAccountDoesNotDependOnEncryptionKrakenOrSecretEntities() throws Exception {
        Path root = Path.of("src/main/java/com/hope/trading/trading_core/brokeraccount");
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertFalse(source.contains("javax.crypto"));
                assertFalse(source.contains("broker_service"));
                assertFalse(source.contains(".kraken."));
                assertFalse(source.contains("EncryptedSecret"));
                assertFalse(source.contains("CredentialMaterial"));
            }
        }
    }
}
