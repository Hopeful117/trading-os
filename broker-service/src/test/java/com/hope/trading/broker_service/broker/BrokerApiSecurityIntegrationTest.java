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
    @Test void internalApiRequiresJwtAndValidatesDtos() throws Exception {mvc.perform(post("/internal/v1/executions").contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isUnauthorized());mvc.perform(post("/internal/v1/executions").header("Authorization","Bearer "+token()).contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isBadRequest());}
    @Test void riskSnapshotUsesAuthenticatedOwner() throws Exception {
        UUID account=UUID.randomUUID(),owner=UUID.fromString("11111111-1111-1111-1111-111111111111");
        Instant from=Instant.parse("2026-08-01T00:00:00Z"),to=Instant.parse("2026-08-02T00:00:00Z");
        when(riskSnapshots.get(owner,account,from,to)).thenReturn(new RiskSnapshot(account,1,to,
                SnapshotCompleteness.COMPLETE,List.of(),Map.of(),new AccountRiskFacts("USD",null,null,null),
                List.of(),List.of(),List.of()));
        mvc.perform(get("/internal/v1/broker-accounts/{id}/risk-snapshot",account)
                .queryParam("from",from.toString()).queryParam("to",to.toString())
                .header("Authorization","Bearer "+token())).andExpect(status().isOk());
        verify(riskSnapshots).get(owner,account,from,to);
    }
    @Test void riskSnapshotRejectsCrossAccountAccess() throws Exception {
        UUID account=UUID.randomUUID(),owner=UUID.fromString("11111111-1111-1111-1111-111111111111");
        Instant from=Instant.parse("2026-08-01T00:00:00Z"),to=Instant.parse("2026-08-02T00:00:00Z");
        when(riskSnapshots.get(owner,account,from,to)).thenThrow(new BrokerAuthorizationException("denied"));
        mvc.perform(get("/internal/v1/broker-accounts/{id}/risk-snapshot",account)
                .queryParam("from",from.toString()).queryParam("to",to.toString())
                .header("Authorization","Bearer "+token())).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BROKER_AUTHORIZATION_FAILED"));
    }
    private String token(){byte[] key=java.util.Base64.getDecoder().decode("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");return Jwts.builder().subject("11111111-1111-1111-1111-111111111111").issuer("trading-os-test").claim("username","test").claim("role","ROLE_USER").issuedAt(Date.from(Instant.now())).expiration(Date.from(Instant.now().plusSeconds(60))).signWith(Keys.hmacShaKeyFor(key)).compact();}
}
