package com.hope.trading.broker_service.httpClient;

import com.hope.trading.broker_service.config.KrakenProperties;
import com.hope.trading.broker_service.dto.KrakenAccountBalanceResponse;
import com.hope.trading.broker_service.dto.KrakenOpenPositionResponse;
import com.hope.trading.broker_service.dto.KrakenTickerResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;


@Component
@RequiredArgsConstructor
public class KrakenRestClientImpl implements KrakenHttpClient {
    private final RestClient krakenRestClient;
    private final KrakenProperties krakenProperties;
    private final KrakenSignatureService krakenSignatureService;

    @Override
    public KrakenTickerResponse getTicker(String symbol) {
        return krakenRestClient.get().uri(uriBuilder -> uriBuilder.path("/0/public/Ticker"
                ).queryParam("pair",symbol).build()).retrieve().body(KrakenTickerResponse.class);
    }

    @Override
    public KrakenAccountBalanceResponse getBalances() {

        return privatePost(
                "/0/private/Balance",
                new LinkedMultiValueMap<>(),
                KrakenAccountBalanceResponse.class
        );
    }

    @Override
    public KrakenOpenPositionResponse getOpenPositions(){
        return privatePost("/0/private/OpenPositions",
                new LinkedMultiValueMap<>(),
                KrakenOpenPositionResponse.class

        );
    }

    private void addPrivateHeaders(
            HttpHeaders headers,
            String signature
    ) {
        headers.add(
                "API-Key",
                krakenProperties.getApiKey()
        );

        headers.add(
                "API-Sign",
                signature
        );

        headers.setContentType(
                MediaType.APPLICATION_FORM_URLENCODED
        );
    }
    private String generateNonce() {
        return String.valueOf(System.currentTimeMillis());
    }

    private <T> T privatePost(
            String path,
            MultiValueMap<String, String> body,
            Class<T> responseType
    ) {

        String nonce = generateNonce();

        body.add(
                "nonce",
                nonce
        );


        String signature =
                krakenSignatureService.generateSignature(
                        path,
                        body.toSingleValueMap()
                );


        HttpHeaders headers = new HttpHeaders();

        addPrivateHeaders(
                headers,
                signature
        );


        return krakenRestClient.post()
                .uri(path)
                .headers(httpHeaders ->
                        httpHeaders.addAll(headers)
                )
                .body(body)
                .retrieve()
                .body(responseType);
    }


}
