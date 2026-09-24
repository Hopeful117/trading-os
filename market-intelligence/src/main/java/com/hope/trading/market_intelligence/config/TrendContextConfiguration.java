package com.hope.trading.market_intelligence.config;

import com.hope.trading.market_intelligence.domain.trendcontext.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.EnumMap;

@Configuration
public class TrendContextConfiguration {
    @Bean
    TrendContextEngine trendContextEngine() {
        return new TrendContextEngine();
    }

    @Bean
    TrendContextProfile trendContextProfile(
            @Value("${intelligence.trend-context.bias-interval:FOUR_HOURS}") String biasInterval,
            @Value("${intelligence.trend-context.setup-interval:ONE_HOUR}") String setupInterval,
            @Value("${intelligence.trend-context.trigger-interval:FIFTEEN_MINUTES}") String triggerInterval,
            @Value("${intelligence.trend-context.requested-candles:60}") int requestedCandles,
            @Value("${intelligence.trend-context.minimum-eligible-candles:60}") int minimumEligibleCandles
    ) {
        EnumMap<TrendContextRole, TrendContextRoleDefinition> roles =
                new EnumMap<>(TrendContextRole.class);
        roles.put(TrendContextRole.BIAS, definition(
                TrendContextRole.BIAS, biasInterval, true, requestedCandles, minimumEligibleCandles));
        roles.put(TrendContextRole.SETUP, definition(
                TrendContextRole.SETUP, setupInterval, true, requestedCandles, minimumEligibleCandles));
        roles.put(TrendContextRole.TRIGGER, definition(
                TrendContextRole.TRIGGER, triggerInterval, false, requestedCandles, minimumEligibleCandles));
        return TrendContextProfile.conservativeSwingV1(roles);
    }

    private TrendContextRoleDefinition definition(
            TrendContextRole role, String interval, boolean required,
            int requestedCandles, int minimumEligibleCandles) {
        return new TrendContextRoleDefinition(
                role, interval, duration(interval), required,
                minimumEligibleCandles, requestedCandles);
    }

    private Duration duration(String interval) {
        return switch (interval.trim().toUpperCase()) {
            case "ONE_MINUTE" -> Duration.ofMinutes(1);
            case "FIVE_MINUTES" -> Duration.ofMinutes(5);
            case "FIFTEEN_MINUTES" -> Duration.ofMinutes(15);
            case "THIRTY_MINUTES" -> Duration.ofMinutes(30);
            case "ONE_HOUR" -> Duration.ofHours(1);
            case "FOUR_HOURS" -> Duration.ofHours(4);
            case "ONE_DAY" -> Duration.ofDays(1);
            default -> throw new IllegalArgumentException("Unsupported Trend Context interval: " + interval);
        };
    }
}
