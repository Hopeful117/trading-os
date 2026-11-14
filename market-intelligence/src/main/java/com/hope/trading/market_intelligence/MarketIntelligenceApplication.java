package com.hope.trading.market_intelligence;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.hope.trading.market_intelligence.config.PassiveMarketUniverseProperties;
import com.hope.trading.market_intelligence.config.ResearchLifecycleProperties;
import com.hope.trading.market_intelligence.config.PassiveDispatchProperties;

@SpringBootApplication
@EnableFeignClients
@EnableConfigurationProperties({ResearchLifecycleProperties.class, PassiveMarketUniverseProperties.class,
        PassiveDispatchProperties.class})
public class MarketIntelligenceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MarketIntelligenceApplication.class, args);
    }
}
