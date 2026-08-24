package com.hope.trading.trading_core.helper;

import com.hope.trading.trading_core.dto.RulesDto;
import com.hope.trading.trading_core.dto.RulesRequest;
import com.hope.trading.trading_core.model.Rules;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * STORY-0020A-3C: protects the risk-rules mapping contract — every limit
 * (percentages, trade counts, leverage) must survive DTO↔entity translation
 * losslessly, because these values gate the risk engine.
 */
class RulesMapperTest {

    private final RulesMapper mapper = new RulesMapper();

    private Rules rules() {
        Rules rules = new Rules();
        rules.setRulesId(UUID.randomUUID());
        rules.setName("conservative");
        rules.setActive(true);
        rules.setMaxRiskPerTrade(new BigDecimal("0.02"));
        rules.setMaxDailyLoss(new BigDecimal("0.05"));
        rules.setMaxTotalDrawdown(new BigDecimal("0.10"));
        rules.setMaxTradesPerDay(5);
        rules.setCooldownMinutesBetweenTrades(15);
        rules.setMaxLeverage(new BigDecimal("3"));
        rules.setAllowedSessions("LONDON,NEW_YORK");
        return rules;
    }

    @Test
    void toDtoPreservesEveryRiskLimitLosslessly() {
        Rules entity = rules();

        RulesDto dto = mapper.toDto(entity);

        assertThat(dto.getRulesId()).isEqualTo(entity.getRulesId());
        assertThat(dto.getName()).isEqualTo("conservative");
        assertThat(dto.isActive()).isTrue();
        assertThat(dto.getMaxRiskPerTrade()).isEqualByComparingTo("0.02");
        assertThat(dto.getMaxDailyLoss()).isEqualByComparingTo("0.05");
        assertThat(dto.getMaxTotalDrawdown()).isEqualByComparingTo("0.10");
        assertThat(dto.getMaxTradesPerDay()).isEqualTo(5);
        assertThat(dto.getCooldownMinutesBetweenTrades()).isEqualTo(15);
        assertThat(dto.getMaxLeverage()).isEqualByComparingTo("3");
        assertThat(dto.getAllowedSessions()).isEqualTo("LONDON,NEW_YORK");
    }

    @Test
    void requestToEntityRoundTripsAllLimits() {
        RulesRequest request = new RulesRequest();
        request.setName("aggressive");
        request.setActive(true);
        request.setMaxRiskPerTrade(new BigDecimal("0.05"));
        request.setMaxDailyLoss(new BigDecimal("0.10"));
        request.setMaxTotalDrawdown(new BigDecimal("0.20"));
        request.setMaxTradesPerDay(20);
        request.setCooldownMinutesBetweenTrades(5);
        request.setMaxLeverage(new BigDecimal("10"));
        request.setAllowedSessions("ALL");

        Rules entity = mapper.toEntity(request);

        assertThat(entity.getName()).isEqualTo("aggressive");
        assertThat(entity.getMaxRiskPerTrade()).isEqualByComparingTo("0.05");
        assertThat(entity.getMaxDailyLoss()).isEqualByComparingTo("0.10");
        assertThat(entity.getMaxTotalDrawdown()).isEqualByComparingTo("0.20");
        assertThat(entity.getMaxTradesPerDay()).isEqualTo(20);
        assertThat(entity.getCooldownMinutesBetweenTrades()).isEqualTo(5);
        assertThat(entity.getMaxLeverage()).isEqualByComparingTo("10");
        assertThat(entity.getAllowedSessions()).isEqualTo("ALL");
    }

    @Test
    void toDtoHandlesNullFinancialFieldsGracefully() {
        Rules entity = Rules.builder()
                .name("minimal")
                .active(false)
                .build();

        RulesDto dto = mapper.toDto(entity);

        assertThat(dto.getRulesId()).isNull();
        assertThat(dto.getName()).isEqualTo("minimal");
        assertThat(dto.isActive()).isFalse();
        assertThat(dto.getMaxRiskPerTrade()).isNull();
        assertThat(dto.getMaxDailyLoss()).isNull();
        assertThat(dto.getMaxTotalDrawdown()).isNull();
        assertThat(dto.getMaxTradesPerDay()).isNull();
        assertThat(dto.getCooldownMinutesBetweenTrades()).isNull();
        assertThat(dto.getMaxLeverage()).isNull();
        assertThat(dto.getAllowedSessions()).isNull();
    }

    @Test
    void toEntityHandlesNullFinancialFieldsGracefully() {
        RulesRequest request = new RulesRequest();
        request.setName("bare");

        Rules entity = mapper.toEntity(request);

        assertThat(entity.getRulesId()).isNull();
        assertThat(entity.getName()).isEqualTo("bare");
        assertThat(entity.isActive()).isFalse();
        assertThat(entity.getMaxRiskPerTrade()).isNull();
        assertThat(entity.getMaxDailyLoss()).isNull();
        assertThat(entity.getMaxTotalDrawdown()).isNull();
        assertThat(entity.getMaxTradesPerDay()).isNull();
        assertThat(entity.getCooldownMinutesBetweenTrades()).isNull();
        assertThat(entity.getMaxLeverage()).isNull();
        assertThat(entity.getAllowedSessions()).isNull();
    }
}
