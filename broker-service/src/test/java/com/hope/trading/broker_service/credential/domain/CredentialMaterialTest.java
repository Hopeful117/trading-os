package com.hope.trading.broker_service.credential.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;

@ExtendWith(OutputCaptureExtension.class)
class CredentialMaterialTest {
    @Test
    void stringRepresentationNeverLeaksSecrets() {
        try (CredentialMaterial material = new CredentialMaterial(
                "FAKE_API_KEY_1234".toCharArray(),
                "FAKE_SENTINEL_SECRET_VALUE".toCharArray(), null)) {
            assertFalse(material.toString().contains("FAKE"));
            assertFalse(material.toString().contains("SENTINEL"));
        }
    }

    @Test
    void sentinelIsNotWrittenToApplicationLogs(CapturedOutput output) {
        try (CredentialMaterial ignored = new CredentialMaterial(
                "FAKE_API_KEY_1234".toCharArray(),
                "FAKE_SENTINEL_SECRET_VALUE".toCharArray(), null)) {
            // Constructing and clearing credential material must remain silent.
        }
        assertFalse(output.getAll().contains("FAKE_SENTINEL_SECRET_VALUE"));
    }
}
