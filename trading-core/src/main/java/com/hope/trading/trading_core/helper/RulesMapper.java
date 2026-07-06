package com.hope.trading.trading_core.helper;

import com.hope.trading.trading_core.dto.RulesDto;
import com.hope.trading.trading_core.dto.RulesRequest;
import com.hope.trading.trading_core.model.Rules;
import org.springframework.stereotype.Component;

@Component
public class RulesMapper {
    public RulesDto toDto(Rules rules){
        return RulesDto.builder()
                .rulesId(rules.getRulesId())
                .name(rules.getName())
                .active(rules.isActive())
                .maxRiskPerTrade(rules.getMaxRiskPerTrade())
                .maxDailyLoss(rules.getMaxDailyLoss())
                .maxTotalDrawdown(rules.getMaxTotalDrawdown())
                .maxTradesPerDay(rules.getMaxTradesPerDay())
                .cooldownMinutesBetweenTrades(rules.getCooldownMinutesBetweenTrades())
                .maxLeverage(rules.getMaxLeverage())
                .allowedSessions(rules.getAllowedSessions())
                .build();
    }

    public Rules toEntity (RulesRequest rulesRequest){
        return Rules.builder()
                .rulesId(rulesRequest.getRulesId())
                .name(rulesRequest.getName())
                .active(rulesRequest.isActive())
                .maxRiskPerTrade(rulesRequest.getMaxRiskPerTrade())
                .maxDailyLoss(rulesRequest.getMaxDailyLoss())
                .maxTotalDrawdown(rulesRequest.getMaxTotalDrawdown())
                .maxTradesPerDay(rulesRequest.getMaxTradesPerDay())
                .cooldownMinutesBetweenTrades(rulesRequest.getCooldownMinutesBetweenTrades())
                .maxLeverage(rulesRequest.getMaxLeverage())
                .allowedSessions(rulesRequest.getAllowedSessions())
                .build();
    }
}
