package com.hope.trading.broker_service.broker.infrastructure.provider.kraken.authentication;

import com.hope.trading.broker_service.credential.domain.CredentialMaterial;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public final class KrakenFuturesRequestSigner {
    private static final String DERIVATIVES_PREFIX = "/derivatives";

    public String sign(String path, String nonce, Map<String, String> parameters,
                       CredentialMaterial credentials) {
        char[] secret = credentials.copyApiSecret();
        try {
            String encodedParameters = parameters.entrySet().stream()
                    .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                    .collect(Collectors.joining("&"));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(encodedParameters.getBytes(StandardCharsets.UTF_8));
            digest.update((nonce + stripDerivativesPrefix(path)).getBytes(StandardCharsets.UTF_8));
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(new String(secret)), "HmacSHA512"));
            return Base64.getEncoder().encodeToString(mac.doFinal(digest.digest()));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign Kraken Futures request", exception);
        } finally {
            java.util.Arrays.fill(secret, '\0');
        }
    }

    private String stripDerivativesPrefix(String path) {
        return path.startsWith(DERIVATIVES_PREFIX)
                ? path.substring(DERIVATIVES_PREFIX.length())
                : path;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
