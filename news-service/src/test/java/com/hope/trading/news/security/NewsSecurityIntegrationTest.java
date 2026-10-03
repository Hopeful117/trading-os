package com.hope.trading.news.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:news-security;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "eureka.client.enabled=false",
        "news.provider.enabled=false",
        "security.jwt.secret=YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFh",
        "security.jwt.issuer=trading-os-test",
        "security.service-jwt.enabled=true",
        "security.service-jwt.authorized-callers=market-intelligence",
        "security.service-jwt.trusted.market-intelligence=YmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJi",
        "security.service-jwt.trusted.other-service=YmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJi"
})
class NewsSecurityIntegrationTest {
    private static final String USER_SECRET =
            "YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFh";
    private static final String SERVICE_SECRET =
            "YmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJi";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void unauthenticatedPublicNewsReadIsRejected() throws Exception {
        mvc.perform(get("/api/v1/news/events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidUserJwtIsRejected() throws Exception {
        mvc.perform(get("/api/v1/news/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserCanReadPublicNews() throws Exception {
        mvc.perform(get("/api/v1/news/events")
                        .header(HttpHeaders.AUTHORIZATION, userBearer()))
                .andExpect(status().isOk());
    }

    @Test
    void internalNewsRouteRequiresServiceAuthentication() throws Exception {
        mvc.perform(get("/internal/v1/news/context/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthorizedServiceCallerIsRejected() throws Exception {
        mvc.perform(get("/internal/v1/news/context/" + UUID.randomUUID())
                        .header("X-Service-Authorization", serviceBearer("other-service")))
                .andExpect(status().isForbidden());
    }

    @Test
    void authorizedServiceCallerCanReachInternalNewsRoute() throws Exception {
        mvc.perform(get("/internal/v1/news/context/" + UUID.randomUUID())
                        .header("X-Service-Authorization", serviceBearer("market-intelligence")))
                .andExpect(status().isOk());
    }

    private static String userBearer() {
        return "Bearer " + Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("username", "trader")
                .claim("email", "trader@example.com")
                .claim("role", "ROLE_USER")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .issuer("trading-os-test")
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(USER_SECRET)))
                .compact();
    }

    private static String serviceBearer(String issuer) {
        return "Bearer " + Jwts.builder()
                .subject(issuer)
                .issuer(issuer)
                .audience().add("news-service").and()
                .claim("type", "service")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(serviceKey())
                .compact();
    }

    private static SecretKey serviceKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(SERVICE_SECRET));
    }
}
