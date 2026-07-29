package com.hope.trading.broker_service.kraken.credential;

import com.hope.trading.broker_service.connection.domain.BrokerPermission;
import com.hope.trading.broker_service.credential.domain.CredentialMaterial;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class KrakenRestCredentialProbe implements KrakenCredentialProbe {
    private final RestClient restClient;
    private final AtomicLong nonce = new AtomicLong(System.currentTimeMillis());

    public KrakenRestCredentialProbe(RestClient krakenRestClient) {
        this.restClient = krakenRestClient;
    }

    @Override
    public ProbeResult probe(CredentialMaterial credentials) {
        EnumSet<BrokerPermission> granted = EnumSet.noneOf(BrokerPermission.class);
        List<ProbeCall> calls = List.of(
                new ProbeCall("/0/private/Balance", Set.of(BrokerPermission.READ_ACCOUNT, BrokerPermission.READ_BALANCES)),
                new ProbeCall("/0/private/OpenPositions", Set.of(BrokerPermission.READ_POSITIONS)),
                new ProbeCall("/0/private/OpenOrders", Set.of(BrokerPermission.READ_ORDERS)),
                new ProbeCall("/0/private/TradesHistory", Set.of(BrokerPermission.READ_TRADE_HISTORY))
        );
        for (ProbeCall call : calls) {
            ProbeOutcome outcome = invoke(call.path(), credentials);
            if (outcome == ProbeOutcome.SUCCESS) {
                granted.addAll(call.permissions());
            } else if (outcome != ProbeOutcome.PERMISSION_DENIED) {
                return new ProbeResult(granted, outcome);
            }
        }
        return new ProbeResult(granted, granted.size() < 5 ? ProbeOutcome.PERMISSION_DENIED : ProbeOutcome.SUCCESS);
    }

    @SuppressWarnings("unchecked")
    private ProbeOutcome invoke(String path, CredentialMaterial credentials) {
        char[] apiKey = credentials.copyApiKey();
        char[] apiSecret = credentials.copyApiSecret();
        try {
            String nonceValue = Long.toString(nonce.incrementAndGet());
            LinkedMultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("nonce", nonceValue);
            String signature = sign(path, nonceValue + "nonce=" + nonceValue, apiSecret);
            Map<String, Object> response = restClient.post().uri(path)
                    .header("API-Key", new String(apiKey))
                    .header("API-Sign", signature)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body).retrieve().body(Map.class);
            if (response == null || !(response.get("error") instanceof List<?> errors)) {
                return ProbeOutcome.UNEXPECTED_RESPONSE;
            }
            if (errors.isEmpty()) return ProbeOutcome.SUCCESS;
            return classify(errors.stream().map(String::valueOf).toList());
        } catch (ResourceAccessException exception) {
            return ProbeOutcome.UNAVAILABLE;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 429) return ProbeOutcome.RATE_LIMITED;
            if (exception.getStatusCode().is5xxServerError()) return ProbeOutcome.UNAVAILABLE;
            return ProbeOutcome.UNEXPECTED_RESPONSE;
        } catch (RuntimeException exception) {
            return ProbeOutcome.UNEXPECTED_RESPONSE;
        } finally {
            java.util.Arrays.fill(apiKey, '\0');
            java.util.Arrays.fill(apiSecret, '\0');
        }
    }

    private ProbeOutcome classify(List<String> errors) {
        String joined = String.join(" ", errors).toLowerCase();
        if (joined.contains("invalid key") || joined.contains("invalid signature")) return ProbeOutcome.INVALID_CREDENTIALS;
        if (joined.contains("permission denied")) return ProbeOutcome.PERMISSION_DENIED;
        if (joined.contains("rate limit") || joined.contains("throttled")) return ProbeOutcome.RATE_LIMITED;
        if (joined.contains("unavailable") || joined.contains("service:busy")) return ProbeOutcome.UNAVAILABLE;
        return ProbeOutcome.UNEXPECTED_RESPONSE;
    }

    private String sign(String path, String postData, char[] encodedSecret) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(postData.getBytes(StandardCharsets.UTF_8));
            byte[] pathBytes = path.getBytes(StandardCharsets.UTF_8);
            byte[] message = new byte[pathBytes.length + hash.length];
            System.arraycopy(pathBytes, 0, message, 0, pathBytes.length);
            System.arraycopy(hash, 0, message, pathBytes.length, hash.length);
            byte[] secret = Base64.getDecoder().decode(new String(encodedSecret));
            try {
                Mac mac = Mac.getInstance("HmacSHA512");
                mac.init(new SecretKeySpec(secret, "HmacSHA512"));
                return Base64.getEncoder().encodeToString(mac.doFinal(message));
            } finally {
                java.util.Arrays.fill(secret, (byte) 0);
            }
        } catch (Exception exception) {
            throw new IllegalArgumentException("Credential signature format is invalid");
        }
    }

    private record ProbeCall(String path, Set<BrokerPermission> permissions) {
    }
}
