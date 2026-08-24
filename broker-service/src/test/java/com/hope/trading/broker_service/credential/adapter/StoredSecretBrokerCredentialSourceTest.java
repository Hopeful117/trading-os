package com.hope.trading.broker_service.credential.adapter;

import com.hope.trading.broker_service.credential.domain.CredentialMaterial;
import com.hope.trading.broker_service.secret.application.SecretReader;
import com.hope.trading.broker_service.secret.domain.CredentialReference;
import com.hope.trading.broker_service.secret.domain.PlainSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StoredSecretBrokerCredentialSourceTest {
    private SecretReader reader;
    private StoredSecretBrokerCredentialSource source;

    @BeforeEach
    void setUp() {
        reader = mock(SecretReader.class);
        source = new StoredSecretBrokerCredentialSource(reader);
    }

    @Test
    void resolveWithValidByteFormatReturnsCredentialMaterial() {
        byte[] apiKey = "test-api-key-12345678".getBytes(StandardCharsets.UTF_8);
        byte[] apiSecret = "test-api-secret-value-1234567890123456".getBytes(StandardCharsets.UTF_8);
        byte[] passphrase = "my-passphrase".getBytes(StandardCharsets.UTF_8);

        byte[] payload = buildPayload(apiKey, apiSecret, passphrase);
        CredentialReference ref = new CredentialReference(UUID.randomUUID());

        when(reader.read(ref)).thenReturn(new PlainSecret(payload));

        CredentialMaterial result = source.resolve(ref);

        assertThat(new String(result.copyApiKey())).isEqualTo("test-api-key-12345678");
        assertThat(new String(result.copyApiSecret())).isEqualTo("test-api-secret-value-1234567890123456");
        assertThat(new String(result.copyPassphrase())).isEqualTo("my-passphrase");
    }

    @Test
    void resolveWithInvalidLengthThrowsIllegalStateException() {
        byte[] garbage = new byte[]{0, 0, 0, 50, 1, 2, 3};
        CredentialReference ref = new CredentialReference(UUID.randomUUID());

        when(reader.read(ref)).thenReturn(new PlainSecret(garbage));

        assertThatThrownBy(() -> source.resolve(ref))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("format is invalid");
    }

    @Test
    void resolveWithTruncatedDataThrowsIllegalStateException() {
        byte[] apiKey = "valid-key".getBytes(StandardCharsets.UTF_8);
        byte[] apiSecret = "valid-secret-value".getBytes(StandardCharsets.UTF_8);
        byte[] passphrase = "p".getBytes(StandardCharsets.UTF_8);

        byte[] payload = buildPayload(apiKey, apiSecret, passphrase);
        CredentialReference ref = new CredentialReference(UUID.randomUUID());

        when(reader.read(ref)).thenReturn(new PlainSecret(payload));

        CredentialMaterial result = source.resolve(ref);

        assertThat(new String(result.copyApiKey())).isEqualTo("valid-key");
        assertThat(new String(result.copyApiSecret())).isEqualTo("valid-secret-value");
        assertThat(new String(result.copyPassphrase())).isEqualTo("p");
    }

    @Test
    void resolveWithNegativeLengthThrowsIllegalStateException() {
        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.putInt(-1);
        CredentialReference ref = new CredentialReference(UUID.randomUUID());

        when(reader.read(ref)).thenReturn(new PlainSecret(buf.array()));

        assertThatThrownBy(() -> source.resolve(ref))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("format is invalid");
    }

    private byte[] buildPayload(byte[]... parts) {
        int totalSize = 0;
        for (byte[] part : parts) {
            totalSize += 4 + part.length;
        }
        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        for (byte[] part : parts) {
            buffer.putInt(part.length);
            buffer.put(part);
        }
        return buffer.array();
    }
}
