package com.hope.trading.broker_service.architecture;

import com.hope.trading.broker_service.connection.api.BrokerConnectionResponse;
import com.hope.trading.broker_service.connection.api.CredentialValidationResponse;
import com.hope.trading.broker_service.credential.domain.CredentialMaterial;
import com.hope.trading.broker_service.secret.domain.EncryptedSecret;
import com.hope.trading.broker_service.secret.domain.PlainSecret;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class BrokerCredentialArchitectureTest {
    @Test
    void responseDtosNeverReferenceSecretTypes() {
        List<Class<?>> forbidden = List.of(CredentialMaterial.class, PlainSecret.class, EncryptedSecret.class);
        for (Class<?> response : List.of(BrokerConnectionResponse.class, CredentialValidationResponse.class)) {
            for (RecordComponent component : response.getRecordComponents()) {
                assertFalse(forbidden.contains(component.getType()));
            }
        }
    }

    @Test
    void commonConnectionDomainDoesNotDependOnKrakenOrSecretInfrastructure() throws Exception {
        Path root = Path.of("src/main/java/com/hope/trading/broker_service/connection/domain");
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertFalse(source.contains("broker_service.kraken"));
                assertFalse(source.contains("secret.adapter"));
            }
        }
    }
}
