package com.hope.trading.broker_service.broker.infrastructure.provider.kraken.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import com.hope.trading.broker_service.credential.domain.CredentialMaterial;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KrakenFuturesRequestSignerTest {
    @Test
    void producesDeterministicSignatureForDerivativesPath() {
        var signer = new KrakenFuturesRequestSigner();
        try (var credentials = new CredentialMaterial(
                "public-key".toCharArray(), "c2VjcmV0LXByaXZhdGUta2V5".toCharArray(), null)) {
            String first = signer.sign("/derivatives/api/v3/accounts", "123456", Map.of(), credentials);
            String second = signer.sign("/derivatives/api/v3/accounts", "123456", Map.of(), credentials);
            assertThat(first).isEqualTo(second).isNotBlank();
        }
    }
}
