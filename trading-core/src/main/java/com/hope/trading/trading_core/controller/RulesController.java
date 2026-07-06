package com.hope.trading.trading_core.controller;

import com.hope.trading.trading_core.dto.RulesDto;
import com.hope.trading.trading_core.dto.RulesRequest;
import com.hope.trading.trading_core.service.RulesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller for managing rules-related operations.
 * Provides endpoints for creating and retrieving rules.
 */
@RestController
@RequestMapping("/api/v1/rules")
@RequiredArgsConstructor
public class RulesController {
    private final RulesService rulesService;

    /**
     * Create a new set of rules based on the provided RulesRequest. The request is validated, and if successful, a new set of rules is created and returned as a RulesDto.
     * @param request the rules request
     * @return the created rules
     */
    @PostMapping
    public ResponseEntity<RulesDto> createRules(@RequestBody RulesRequest request) {
        RulesDto createdRules = rulesService.createRules(request);
        return ResponseEntity.ok(createdRules);
    }

    /**
     *  Fetch the rules based on the provided rules id
     * @param id the id of the rules
     * @return the rules
     */
    @GetMapping("/{id}")
    public ResponseEntity<RulesDto> getRulesById(@PathVariable UUID id) {
        RulesDto rulesDto = rulesService.getRulesById(id);
        return ResponseEntity.ok(rulesDto);
    }

}
