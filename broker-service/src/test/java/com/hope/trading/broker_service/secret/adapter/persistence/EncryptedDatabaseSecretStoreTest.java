package com.hope.trading.broker_service.secret.adapter.persistence;

import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import com.hope.trading.broker_service.secret.adapter.crypto.AesGcmSecretCipher;
import com.hope.trading.broker_service.secret.adapter.crypto.EnvironmentKeyProvider;
import com.hope.trading.broker_service.secret.adapter.crypto.SecretKeyProperties;
import com.hope.trading.broker_service.secret.application.SecretRevokedException;
import com.hope.trading.broker_service.secret.domain.NewSecret;
import com.hope.trading.broker_service.secret.domain.SecretMetadata;
import com.hope.trading.broker_service.secret.domain.SecretStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@ActiveProfiles("test")
class EncryptedDatabaseSecretStoreTest {
    @Autowired
    private BrokerSecretRepository repository;

    @Test
    void writesReadsRotatesAndRevokesOneActiveVersion() {
        UUID accountId = UUID.randomUUID();
        EncryptedDatabaseSecretStore store = store();
        SecretMetadata metadata = new SecretMetadata(accountId, BrokerProviderId.KRAKEN, "••••1234");
        var first = store.write(secret("FIRST_FAKE_SECRET"), metadata);
        try (var resolved = store.read(first)) {
            assertArrayEquals(bytes("FIRST_FAKE_SECRET"), resolved.copyValue());
        }

        var second = store.rotate(first, secret("SECOND_FAKE_SECRET"), metadata);
        assertThrows(SecretRevokedException.class, () -> store.read(first));
        assertEquals(1, repository.countByBrokerAccountIdAndStatus(accountId, SecretStatus.ACTIVE));
        assertEquals(1, repository.countByBrokerAccountIdAndStatus(accountId, SecretStatus.REVOKED));

        store.revoke(second, "TEST");
        assertThrows(SecretRevokedException.class, () -> store.read(second));
        assertEquals(0, repository.countByBrokerAccountIdAndStatus(accountId, SecretStatus.ACTIVE));
    }

    private EncryptedDatabaseSecretStore store() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        var cipher = new AesGcmSecretCipher(
                new EnvironmentKeyProvider(new SecretKeyProperties(key, "test-v1")), new SecureRandom());
        return new EncryptedDatabaseSecretStore(repository, cipher,
                Clock.fixed(Instant.parse("2026-07-29T10:00:00Z"), ZoneOffset.UTC));
    }

    private NewSecret secret(String value) {
        return new NewSecret(bytes(value));
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
