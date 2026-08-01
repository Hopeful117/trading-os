package com.hope.trading.broker_service.kraken.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import io.micrometer.observation.ObservationRegistry;

@Configuration
@RequiredArgsConstructor
public class KrakenRestClientConfig {
    private final KrakenProperties properties;

    @Bean
    public RestClient krakenRestClient(ObservationRegistry observations) {
        HttpClient httpClient=HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build();
        JdkClientHttpRequestFactory requestFactory=new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return RestClient.builder().observationRegistry(observations).requestFactory(requestFactory).baseUrl(properties.getBaseUrl()).build();

    }
}
