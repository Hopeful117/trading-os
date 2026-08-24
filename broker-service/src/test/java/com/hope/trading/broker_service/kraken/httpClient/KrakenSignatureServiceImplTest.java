package com.hope.trading.broker_service.kraken.httpClient;

import com.hope.trading.broker_service.kraken.config.KrakenProperties;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KrakenSignatureServiceImplTest {

    private final KrakenProperties krakenProperties = new KrakenProperties();
    {
        krakenProperties.setApiSecret("dGVzdC1zZWNyZXQta2V5LTEyMzQ1Njc4OTBhYmNkZWZnaA==");
    }
    private final KrakenSignatureServiceImpl service = new KrakenSignatureServiceImpl(krakenProperties);

    @Test
    void signatureGeneratedWithValidInputs() {
        String path = "/0/private/Balance";
        Map<String, String> body = Map.of(
                "nonce", "1234567890",
                "symbol", "XBT/EUR"
        );

        String signature = service.generateSignature(path, body);

        assertThat(signature).isNotNull();
        assertThat(signature).isNotBlank();
    }

    @Test
    void illegalStateThrownWhenSecretCannotBeDecoded() {
        KrakenProperties badProps = new KrakenProperties();
        badProps.setApiSecret("not-valid-base64!!!");
        KrakenSignatureServiceImpl badService = new KrakenSignatureServiceImpl(badProps);

        String path = "/0/private/Balance";
        Map<String, String> body = Map.of("nonce", "1234567890");

        assertThatThrownBy(() -> badService.generateSignature(path, body))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unable to generate Kraken API signature");
    }

    @Test
    void emptyBodyStillProducesSignature() {
        String path = "/0/private/Balance";
        Map<String, String> body = Collections.emptyMap();

        String signature = service.generateSignature(path, body);

        assertThat(signature).isNotNull();
    }
}
