package com.hope.trading.broker_service.kraken.httpClient;

import com.hope.trading.broker_service.kraken.config.KrakenProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KrakenSignatureServiceImpl implements KrakenSignatureService{
    private final KrakenProperties krakenProperties;
    @Override
    public String generateSignature(
            String path,
            Map<String, String> body
    ) {

        try {

            String postData = body.entrySet()
                    .stream()
                    .map(entry ->
                            entry.getKey() + "=" + entry.getValue()
                    )
                    .collect(Collectors.joining("&"));


            String nonce = body.get("nonce");


            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");

            byte[] hash = sha256.digest(
                    (nonce + postData)
                            .getBytes(StandardCharsets.UTF_8)
            );


            byte[] message = new byte[path.length() + hash.length];

            System.arraycopy(
                    path.getBytes(StandardCharsets.UTF_8),
                    0,
                    message,
                    0,
                    path.length()
            );

            System.arraycopy(
                    hash,
                    0,
                    message,
                    path.length(),
                    hash.length
            );


            Mac mac = Mac.getInstance("HmacSHA512");

            byte[] secretKey = Base64.getDecoder()
                    .decode(krakenProperties.getApiSecret());


            SecretKeySpec keySpec =
                    new SecretKeySpec(
                            secretKey,
                            "HmacSHA512"
                    );


            mac.init(keySpec);


            byte[] signature = mac.doFinal(message);


            return Base64.getEncoder()
                    .encodeToString(signature);


        } catch (Exception e) {

            throw new IllegalStateException(
                    "Unable to generate Kraken API signature",
                    e
            );
        }
    }
}
