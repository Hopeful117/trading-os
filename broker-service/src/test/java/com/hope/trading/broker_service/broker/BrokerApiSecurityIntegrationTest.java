package com.hope.trading.broker_service.broker;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Date;
import com.hope.trading.broker_service.broker.application.service.BrokerOperationServices.GetRiskSnapshotService;
import com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.BrokerAuthorizationException;
import com.hope.trading.broker_service.broker.domain.model.BrokerModels.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class BrokerApiSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean GetRiskSnapshotService riskSnapshots;
    @Test void internalApiRequiresServiceJwtAndValidatesDtos() throws Exception {mvc.perform(post("/internal/v1/executions").contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isUnauthorized());mvc.perform(post("/internal/v1/executions").header("X-Service-Authorization","Bearer "+serviceToken()).contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isBadRequest());}
    @Test void riskSnapshotUsesAuthenticatedOwner() throws Exception {
        UUID account=UUID.randomUUID(),owner=UUID.fromString("11111111-1111-1111-1111-111111111111");
        Instant from=Instant.parse("2026-08-01T00:00:00Z"),to=Instant.parse("2026-08-02T00:00:00Z");
        when(riskSnapshots.get(owner,account,from,to)).thenReturn(new RiskSnapshot(account,1,to,
                SnapshotCompleteness.COMPLETE,List.of(),Map.of(),new AccountRiskFacts("USD",null,null,null),
                List.of(),List.of(),List.of()));
        mvc.perform(get("/internal/v1/broker-accounts/{id}/risk-snapshot",account)
                .queryParam("from",from.toString()).queryParam("to",to.toString())
                .header("X-Actor-Id",UUID.randomUUID().toString())
                .header("X-Service-Authorization","Bearer "+serviceToken(owner))).andExpect(status().isOk());
        verify(riskSnapshots).get(owner,account,from,to);
    }
    @Test void riskSnapshotRejectsCrossAccountAccess() throws Exception {
        UUID account=UUID.randomUUID(),owner=UUID.fromString("11111111-1111-1111-1111-111111111111");
        Instant from=Instant.parse("2026-08-01T00:00:00Z"),to=Instant.parse("2026-08-02T00:00:00Z");
        when(riskSnapshots.get(owner,account,from,to)).thenThrow(new BrokerAuthorizationException("denied"));
        mvc.perform(get("/internal/v1/broker-accounts/{id}/risk-snapshot",account)
                .queryParam("from",from.toString()).queryParam("to",to.toString())
                .header("X-Service-Authorization","Bearer "+serviceToken(owner))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BROKER_AUTHORIZATION_FAILED"));
    }
    @Test void riskSnapshotRequiresDelegatedActor() throws Exception {
        UUID account=UUID.randomUUID();
        Instant from=Instant.parse("2026-08-01T00:00:00Z"),to=Instant.parse("2026-08-02T00:00:00Z");
        mvc.perform(get("/internal/v1/broker-accounts/{id}/risk-snapshot",account)
                .queryParam("from",from.toString()).queryParam("to",to.toString())
                .header("X-Service-Authorization","Bearer "+serviceTokenWithoutActor()))
                .andExpect(status().isForbidden());
    }
    @Test void technicalCapabilitiesRequiresDelegatedActor() throws Exception {
        mvc.perform(get("/internal/v1/broker-accounts/{id}/capabilities",UUID.randomUUID())
                .queryParam("instrument","BTC/USD")
                .header("X-Service-Authorization","Bearer "+serviceTokenWithoutActor()))
                .andExpect(status().isForbidden());
    }
    @Test void marginPreviewRejectsPathAndBodyAccountMismatch() throws Exception {
        UUID pathAccount=UUID.randomUUID(),bodyAccount=UUID.randomUUID();
        mvc.perform(post("/internal/v1/broker-accounts/{id}/margin-preview",pathAccount)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"brokerAccountId\":\""+bodyAccount+"\",\"instrument\":\"BTC/USD\",\"side\":\"BUY\",\"quantity\":1,\"price\":100}")
                .header("X-Service-Authorization","Bearer "+serviceToken()))
                .andExpect(status().isBadRequest());
    }
    @Test void rejectsInvalidSignature() throws Exception {assertUnauthorized(serviceTokenWithKey("broker-service", Instant.now().plusSeconds(60),
            java.util.Base64.getDecoder().decode("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")));}
    @Test void rejectsExpiredToken() throws Exception {assertUnauthorized(serviceTokenWithKey("broker-service", Instant.now().minusSeconds(1),
            java.util.Base64.getDecoder().decode("QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=")));}
    @Test void rejectsWrongAudience() throws Exception {assertUnauthorized(serviceTokenWithKey("market-data", Instant.now().plusSeconds(60),
            java.util.Base64.getDecoder().decode("QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=")));}
    @Test void rejectsUnauthorizedValidCaller() throws Exception {assertForbidden(serviceTokenWithIssuer("other-service", "broker-service", Instant.now().plusSeconds(60),
            java.util.Base64.getDecoder().decode("QkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkI=")));}
    private String serviceToken(){return serviceToken(UUID.fromString("11111111-1111-1111-1111-111111111111"));}
    private String serviceToken(UUID actor){return serviceTokenWithKey("broker-service",Instant.now().plusSeconds(60),java.util.Base64.getDecoder().decode("QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE="),actor);}
    private String serviceTokenWithoutActor(){return serviceTokenWithKey("broker-service",Instant.now().plusSeconds(60),java.util.Base64.getDecoder().decode("QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE="));}
    private String serviceTokenWithKey(String audience,Instant expiration,byte[] key){return serviceTokenWithKey(audience,expiration,key,null);}
    private String serviceTokenWithKey(String audience,Instant expiration,byte[] key,UUID actor){return serviceTokenWithIssuer("trading-core",audience,expiration,key,actor);}
    private String serviceTokenWithIssuer(String issuer,String audience,Instant expiration,byte[] key){return serviceTokenWithIssuer(issuer,audience,expiration,key,null);}
    private String serviceTokenWithIssuer(String issuer,String audience,Instant expiration,byte[] key,UUID actor){var builder=Jwts.builder().subject(issuer).issuer(issuer).audience().add(audience).and().claim("type","service").issuedAt(Date.from(Instant.now())).expiration(Date.from(expiration));if(actor!=null)builder.claim("actor_id",actor.toString());return builder.signWith(Keys.hmacShaKeyFor(key)).compact();}
    private void assertUnauthorized(String token) throws Exception {mvc.perform(get("/internal/v1/broker-accounts/{id}/risk-snapshot",UUID.randomUUID()).queryParam("from",Instant.now().toString()).queryParam("to",Instant.now().plusSeconds(1).toString()).header("X-Service-Authorization","Bearer "+token)).andExpect(status().isUnauthorized());}
    private void assertForbidden(String token) throws Exception {mvc.perform(get("/internal/v1/broker-accounts/{id}/risk-snapshot",UUID.randomUUID()).queryParam("from",Instant.now().toString()).queryParam("to",Instant.now().plusSeconds(1).toString()).header("X-Service-Authorization","Bearer "+token)).andExpect(status().isForbidden());}
}
