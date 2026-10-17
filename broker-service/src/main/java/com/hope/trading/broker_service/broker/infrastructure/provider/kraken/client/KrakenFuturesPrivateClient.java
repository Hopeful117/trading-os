package com.hope.trading.broker_service.broker.infrastructure.provider.kraken.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.BrokerAuthenticationException;
import com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.BrokerProtocolException;
import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.authentication.KrakenFuturesRequestSigner;
import com.hope.trading.broker_service.credential.domain.CredentialMaterial;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public final class KrakenFuturesPrivateClient {
    private static final String ACCOUNTS_PATH = "/derivatives/api/v3/accounts";
    private final RestClient client;
    private final KrakenFuturesRequestSigner signer;
    private final ObjectMapper json = new ObjectMapper();
    private final AtomicLong nonce = new AtomicLong(System.currentTimeMillis());

    public KrakenFuturesPrivateClient(@Qualifier("krakenFuturesRestClient") RestClient client,
                                      KrakenFuturesRequestSigner signer) {
        this.client = client;
        this.signer = signer;
    }

    public JsonNode accounts(CredentialMaterial credentials) {
        String nonceValue = Long.toString(nonce.updateAndGet(previous ->
                Math.max(previous + 1, System.currentTimeMillis())));
        String signature = signer.sign(ACCOUNTS_PATH, nonceValue, Map.of(), credentials);
        char[] apiKey = credentials.copyApiKey();
        String payload;
        try {
            payload = client.get().uri(ACCOUNTS_PATH)
                    .header("APIKey", new String(apiKey))
                    .header("Nonce", nonceValue)
                    .header("Authent", signature)
                    .retrieve().body(String.class);
        } finally {
            java.util.Arrays.fill(apiKey, '\0');
        }
        try {
            JsonNode response = json.readTree(payload);
            String result = response.path("result").asText();
            if ("error".equalsIgnoreCase(result)) {
                String error = response.path("error").asText("Kraken Futures rejected the request");
                if (error.toLowerCase(java.util.Locale.ROOT).contains("auth")) {
                    throw new BrokerAuthenticationException(error);
                }
                throw new BrokerProtocolException(error);
            }
            return response;
        } catch (BrokerAuthenticationException | BrokerProtocolException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BrokerProtocolException("Malformed Kraken Futures response");
        }
    }
}
