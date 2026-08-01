package com.hope.trading.broker_service.broker;

import com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.*;
import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.authentication.KrakenRequestSigner;
import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.client.KrakenRestProviderClient;
import com.hope.trading.broker_service.credential.domain.CredentialMaterial;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class KrakenRestProviderClientTest {
    @Test void translatesProviderErrorsWithoutLeakingPayload(){assertError("EOrder:Insufficient funds",InsufficientFundsException.class);assertError("EOrder:Invalid volume",InvalidOrderException.class);assertError("EGeneral:Permission denied",BrokerAuthorizationException.class);}
    @Test void signsAndMapsSuccessfulResponse(){RestClient.Builder builder=RestClient.builder();MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();var client=new KrakenRestProviderClient(builder.baseUrl("https://kraken.test").build(),new KrakenRequestSigner());server.expect(once(),requestTo("https://kraken.test/0/private/Balance")).andExpect(header("API-Key","12345678")).andRespond(withSuccess("{\"error\":[],\"result\":{\"ZUSD\":\"12.5\"}}",MediaType.APPLICATION_JSON));try(var credentials=credentials()){assertThat(client.privatePost("/0/private/Balance",Map.of(),credentials).path("ZUSD").asText()).isEqualTo("12.5");}server.verify();}
    private void assertError(String providerError,Class<? extends Throwable> expected){RestClient.Builder builder=RestClient.builder();MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();var client=new KrakenRestProviderClient(builder.baseUrl("https://kraken.test").build(),new KrakenRequestSigner());server.expect(once(),anything()).andRespond(withSuccess("{\"error\":[\""+providerError+"\"],\"result\":{}}",MediaType.APPLICATION_JSON));try(var credentials=credentials()){assertThatThrownBy(()->client.privatePost("/0/private/AddOrder",Map.of(),credentials)).isInstanceOf(expected);}server.verify();}
    private CredentialMaterial credentials(){return new CredentialMaterial("12345678".toCharArray(),"MTIzNDU2Nzg5MDEyMzQ1Ng==".toCharArray(),null);}
}
