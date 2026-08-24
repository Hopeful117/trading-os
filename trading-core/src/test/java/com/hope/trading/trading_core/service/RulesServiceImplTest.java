package com.hope.trading.trading_core.service;

import com.hope.trading.trading_core.dto.RulesDto;
import com.hope.trading.trading_core.dto.RulesRequest;
import com.hope.trading.trading_core.helper.RulesMapper;
import com.hope.trading.trading_core.model.Rules;
import com.hope.trading.trading_core.repository.RulesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RulesServiceImplTest {

    private RulesRepository rulesRepository;
    private RulesMapper rulesMapper;
    private RulesServiceImpl service;

    @BeforeEach
    void setUp() {
        rulesRepository = mock(RulesRepository.class);
        rulesMapper = new RulesMapper();
        service = new RulesServiceImpl(rulesRepository, rulesMapper);
    }

    @Test
    void createRulesSavesAndReturnsDto() {
        RulesRequest request = RulesRequest.builder()
                .name("conservative")
                .active(true)
                .maxRiskPerTrade(new BigDecimal("0.01"))
                .maxDailyLoss(new BigDecimal("0.05"))
                .maxTotalDrawdown(new BigDecimal("0.10"))
                .maxTradesPerDay(5)
                .cooldownMinutesBetweenTrades(15)
                .maxLeverage(new BigDecimal("10"))
                .allowedSessions("US,EU")
                .build();
        Rules saved = Rules.builder()
                .rulesId(UUID.randomUUID())
                .name("conservative")
                .active(true)
                .maxRiskPerTrade(new BigDecimal("0.01"))
                .maxDailyLoss(new BigDecimal("0.05"))
                .maxTotalDrawdown(new BigDecimal("0.10"))
                .maxTradesPerDay(5)
                .cooldownMinutesBetweenTrades(15)
                .maxLeverage(new BigDecimal("10"))
                .allowedSessions("US,EU")
                .build();
        when(rulesRepository.save(any(Rules.class))).thenReturn(saved);

        RulesDto result = service.createRules(request);

        assertThat(result.getName()).isEqualTo("conservative");
        assertThat(result.isActive()).isTrue();
        assertThat(result.getMaxRiskPerTrade()).isEqualByComparingTo(new BigDecimal("0.01"));
        verify(rulesRepository).save(any(Rules.class));
    }

    @Test
    void getRulesByIdReturnsDtoWhenFound() {
        UUID id = UUID.randomUUID();
        Rules entity = Rules.builder()
                .rulesId(id)
                .name("aggressive")
                .active(false)
                .maxRiskPerTrade(new BigDecimal("0.03"))
                .maxDailyLoss(new BigDecimal("0.10"))
                .maxTotalDrawdown(new BigDecimal("0.20"))
                .build();
        when(rulesRepository.findById(id)).thenReturn(Optional.of(entity));

        RulesDto result = service.getRulesById(id);

        assertThat(result).isNotNull();
        assertThat(result.getRulesId()).isEqualTo(id);
        assertThat(result.getName()).isEqualTo("aggressive");
    }

    @Test
    void getRulesByIdReturnsNullWhenNotFound() {
        when(rulesRepository.findById(any())).thenReturn(Optional.empty());

        assertThat(service.getRulesById(UUID.randomUUID())).isNull();
    }

    @Test
    void getRulesByNameReturnsDtoWhenFound() {
        Rules entity = Rules.builder()
                .rulesId(UUID.randomUUID())
                .name("scalping")
                .active(true)
                .maxRiskPerTrade(new BigDecimal("0.005"))
                .maxDailyLoss(new BigDecimal("0.03"))
                .maxTotalDrawdown(new BigDecimal("0.06"))
                .build();
        when(rulesRepository.findByName("scalping")).thenReturn(Optional.of(entity));

        RulesDto result = service.getRulesByName("scalping");

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("scalping");
        assertThat(result.isActive()).isTrue();
    }

    @Test
    void getRulesByNameReturnsNullWhenNotFound() {
        when(rulesRepository.findByName("unknown")).thenReturn(Optional.empty());

        assertThat(service.getRulesByName("unknown")).isNull();
    }
}
