package com.hope.trading.market_intelligence.config;

import com.hope.trading.market_intelligence.application.thesis.MarketThesisComposer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import com.hope.trading.market_intelligence.domain.trendcontext.TrendContextProfile;

@Configuration
public class MarketThesisConfiguration {
    @Bean
    MarketThesisComposer marketThesisComposer(Clock clock, TrendContextProfile profile) {
        return new MarketThesisComposer(clock, profile);
    }
}
