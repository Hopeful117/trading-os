package com.hope.trading.broker_service.broker;

import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.authentication.KrakenRequestSigner;
import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.authentication.ProviderCredentialSession;
import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.capability.KrakenFeeScheduleCapability;
import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.client.KrakenRestProviderClient;
import com.hope.trading.broker_service.credential.domain.CredentialMaterial;
import com.hope.trading.broker_service.kraken.config.HistoricalFeeScheduleCaptureProperties;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KrakenFeeScheduleCapabilityTest {
    @Test
    void mapsCurrentPercentageFeesToDecimalFractions() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var client = new KrakenRestProviderClient(builder.baseUrl("https://kraken.test").build(), new KrakenRequestSigner());
        var properties = new HistoricalFeeScheduleCaptureProperties();
        ProviderCredentialSession session = new ProviderCredentialSession() {
            @Override
            public <T> T withCredentials(UUID accountId, Function<CredentialMaterial, T> operation) {
                try (CredentialMaterial material = credentials()) {
                    return operation.apply(material);
                }
            }
        };
        var capability = new KrakenFeeScheduleCapability(session, client, properties);
        server.expect(once(), requestTo("https://kraken.test/0/private/TradeVolume"))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("pair=PF_XBTUSD"),
                        org.hamcrest.Matchers.containsString("aclass=derivatives"),
                        org.hamcrest.Matchers.containsString("fee_schedule=true"))))
                .andRespond(withSuccess("""
                        {"error":[],"result":{"currency":"ZUSD","fees":{"PF_XBTUSD":{"fee":"0.2600"}},
                        "fees_maker":{"PF_XBTUSD":{"fee":"0.1000"}}}}
                        """, MediaType.APPLICATION_JSON));

        var observation = capability.acquire(UUID.randomUUID(), "PF_XBTUSD");

        assertThat(observation.makerRate()).isEqualByComparingTo("0.001");
        assertThat(observation.takerRate()).isEqualByComparingTo("0.0026");
        assertThat(observation.settlementAsset()).isEqualTo("USD");
        server.verify();
    }

    @Test
    void acceptsCanonicalPairKeyAndUsesActiveScheduleWhenMakerFeesAreNotReturned() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var client = new KrakenRestProviderClient(builder.baseUrl("https://kraken.test").build(), new KrakenRequestSigner());
        var properties = new HistoricalFeeScheduleCaptureProperties();
        ProviderCredentialSession session = new ProviderCredentialSession() {
            @Override
            public <T> T withCredentials(UUID accountId, Function<CredentialMaterial, T> operation) {
                try (CredentialMaterial material = credentials()) {
                    return operation.apply(material);
                }
            }
        };
        var capability = new KrakenFeeScheduleCapability(session, client, properties);
        server.expect(once(), requestTo("https://kraken.test/0/private/TradeVolume"))
                .andRespond(withSuccess("""
                        {"error":[],"result":{"currency":"ZUSD","fees":{"XXBTZUSD":{"fee":"0.2600"}},
                        "schedules":[{"pair":"XXBTZUSD","tiers":[{"maker_fee":"0.1000","taker_fee":"0.2600","active":true}]}]}}
                        """, MediaType.APPLICATION_JSON));

        var observation = capability.acquire(UUID.randomUUID(), "XBTUSD");

        assertThat(observation.makerRate()).isEqualByComparingTo("0.001");
        assertThat(observation.takerRate()).isEqualByComparingTo("0.0026");
        assertThat(observation.settlementAsset()).isEqualTo("USD");
        server.verify();
    }

    private CredentialMaterial credentials() {
        return new CredentialMaterial("12345678".toCharArray(), "MTIzNDU2Nzg5MDEyMzQ1Ng==".toCharArray(), null);
    }
}
