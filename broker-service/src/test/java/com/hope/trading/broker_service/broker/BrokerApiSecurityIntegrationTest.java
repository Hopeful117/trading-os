package com.hope.trading.broker_service.broker;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class BrokerApiSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Test void internalApiRequiresJwtAndValidatesDtos() throws Exception {mvc.perform(post("/internal/v1/executions").contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isUnauthorized());mvc.perform(post("/internal/v1/executions").header("Authorization","Bearer "+token()).contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isBadRequest());}
    private String token(){byte[] key=java.util.Base64.getDecoder().decode("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");return Jwts.builder().subject("11111111-1111-1111-1111-111111111111").issuer("trading-os-test").claim("username","test").claim("role","ROLE_USER").issuedAt(Date.from(Instant.now())).expiration(Date.from(Instant.now().plusSeconds(60))).signWith(Keys.hmacShaKeyFor(key)).compact();}
}
