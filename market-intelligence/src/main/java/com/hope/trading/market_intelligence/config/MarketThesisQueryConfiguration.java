package com.hope.trading.market_intelligence.config;

import com.hope.trading.market_intelligence.application.port.MarketThesisRepository;
import com.hope.trading.market_intelligence.application.thesis.MarketThesisQueryService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MarketThesisQueryConfiguration {
    @Bean
    MarketThesisQueryService marketThesisQueryService(MarketThesisRepository repository) {
        return new MarketThesisQueryService(repository);
    }
}
