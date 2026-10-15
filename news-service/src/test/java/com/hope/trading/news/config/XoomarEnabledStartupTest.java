package com.hope.trading.news.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:news-xoomar-startup;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "eureka.client.enabled=false",
        "news.provider.enabled=false",
        "news.xoomar.enabled=true",
        "spring.task.scheduling.enabled=false",
        "security.jwt.secret=YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFh",
        "security.jwt.issuer=trading-os-test",
        "security.service-jwt.enabled=true",
        "security.service-jwt.authorized-callers=market-intelligence",
        "security.service-jwt.trusted.market-intelligence=YmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJi"
})
class XoomarEnabledStartupTest {
    @Autowired
    private RestClient.Builder restClientBuilder;

    @Test
    void startsWithXoomarEnabled() {
        assertThat(restClientBuilder).isNotNull();
    }
}
