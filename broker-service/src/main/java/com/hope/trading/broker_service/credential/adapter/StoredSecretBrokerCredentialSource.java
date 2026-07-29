package com.hope.trading.broker_service.credential.adapter;

import com.hope.trading.broker_service.credential.application.BrokerCredentialSource;
import com.hope.trading.broker_service.credential.domain.CredentialMaterial;
import com.hope.trading.broker_service.secret.application.SecretReader;
import com.hope.trading.broker_service.secret.domain.CredentialReference;
import com.hope.trading.broker_service.secret.domain.PlainSecret;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Component
@ConditionalOnProperty(name = "trading-os.broker.credentials.source", havingValue = "stored")
public class StoredSecretBrokerCredentialSource implements BrokerCredentialSource {
    private final SecretReader reader;

    public StoredSecretBrokerCredentialSource(SecretReader reader) {
        this.reader = reader;
    }

    @Override
    public CredentialMaterial resolve(CredentialReference reference) {
        try (PlainSecret plain = reader.read(reference)) {
            byte[] bytes = plain.copyValue();
            try {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                return new CredentialMaterial(read(buffer), read(buffer), read(buffer));
            } finally {
                Arrays.fill(bytes, (byte) 0);
            }
        }
    }

    private char[] read(ByteBuffer buffer) {
        int length = buffer.getInt();
        if (length < 0 || length > buffer.remaining()) throw new IllegalStateException("Stored credential format is invalid");
        byte[] value = new byte[length];
        buffer.get(value);
        try {
            return new String(value, StandardCharsets.UTF_8).toCharArray();
        } finally {
            Arrays.fill(value, (byte) 0);
        }
    }
}
