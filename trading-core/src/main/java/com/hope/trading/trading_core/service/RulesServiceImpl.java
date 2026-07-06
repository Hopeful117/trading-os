package com.hope.trading.trading_core.service;

import com.hope.trading.trading_core.dto.RulesDto;
import com.hope.trading.trading_core.dto.RulesRequest;
import com.hope.trading.trading_core.helper.RulesMapper;
import com.hope.trading.trading_core.repository.RulesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RulesServiceImpl implements RulesService {
    private final RulesRepository rulesRepository;
    private final RulesMapper rulesMapper;

    @Override
    public RulesDto createRules(RulesRequest request) {
        return rulesMapper.toDto(
                rulesRepository.save(
                        rulesMapper.toEntity(request)
                )
        );
    }

    @Override
    public RulesDto getRulesById(UUID id) {
        return rulesRepository.findById(id)
                .map(rulesMapper::toDto)
                .orElse(null);
    }

    @Override
    public RulesDto getRulesByName(String name) {
        return rulesRepository.findByName(name)
                .map(rulesMapper::toDto)
                .orElse(null);
    }
}
