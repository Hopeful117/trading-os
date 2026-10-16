package com.hope.trading.market_intelligence.application.outcome;

import com.hope.trading.market_intelligence.domain.outcome.MarketOutcomeCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MarketOutcomeConfiguration {
    @Bean
    MarketOutcomeCalculator marketOutcomeCalculator() {
        return new MarketOutcomeCalculator();
    }
}
