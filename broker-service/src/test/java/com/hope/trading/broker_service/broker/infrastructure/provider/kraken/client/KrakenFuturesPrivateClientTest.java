package com.hope.trading.broker_service.broker.infrastructure.provider.kraken.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.authentication.KrakenFuturesRequestSigner;
import com.hope.trading.broker_service.credential.domain.CredentialMaterial;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KrakenFuturesPrivateClientTest {
    @Test
    void callsAccountsUsingFuturesAuthenticationHeaders() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var client = new KrakenFuturesPrivateClient(
                builder.baseUrl("https://futures.test").build(), new KrakenFuturesRequestSigner());
        server.expect(requestTo("https://futures.test/derivatives/api/v3/accounts"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("APIKey", "public-key"))
                .andExpect(header("Nonce", org.hamcrest.Matchers.matchesPattern("\\d+")))
                .andExpect(header("Authent", org.hamcrest.Matchers.not(org.hamcrest.Matchers.blankOrNullString())))
                .andRespond(withSuccess("{\"result\":\"success\",\"accounts\":{}}", MediaType.APPLICATION_JSON));

        try (var credentials = new CredentialMaterial(
                "public-key".toCharArray(), "c2VjcmV0LXByaXZhdGUta2V5".toCharArray(), null)) {
            JsonNodeAssertions.assertSuccess(client.accounts(credentials));
        }
        server.verify();
    }

    private static final class JsonNodeAssertions {
        private static void assertSuccess(com.fasterxml.jackson.databind.JsonNode response) {
            assertThat(response.path("result").asText()).isEqualTo("success");
        }
    }
}
