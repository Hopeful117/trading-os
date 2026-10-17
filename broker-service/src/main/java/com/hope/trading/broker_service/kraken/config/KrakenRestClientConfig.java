package com.hope.trading.broker_service.kraken.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import io.micrometer.observation.ObservationRegistry;

@Configuration
@RequiredArgsConstructor
public class KrakenRestClientConfig {
    private final KrakenProperties properties;

    @Bean
    @Primary
    public RestClient krakenRestClient(ObservationRegistry observations) {
        return build(properties.getBaseUrl(), observations);
    }

    @Bean(name = "krakenFuturesRestClient")
    public RestClient krakenFuturesRestClient(ObservationRegistry observations) {
        return build(properties.getFuturesBaseUrl(), observations);
    }

    private RestClient build(String baseUrl, ObservationRegistry observations) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return RestClient.builder().observationRegistry(observations).requestFactory(requestFactory)
                .baseUrl(baseUrl).build();
    }
}
