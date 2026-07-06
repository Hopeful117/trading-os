package com.hope.trading.trading_core.service;

import com.hope.trading.trading_core.dto.RulesDto;
import com.hope.trading.trading_core.dto.RulesRequest;
import com.hope.trading.trading_core.model.Rules;

import java.util.UUID;

public interface RulesService {
    RulesDto createRules(RulesRequest request);
    RulesDto getRulesById(UUID id);
    RulesDto getRulesByName(String name);
}
