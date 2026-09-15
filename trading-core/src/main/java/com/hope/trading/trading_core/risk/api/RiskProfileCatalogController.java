package com.hope.trading.trading_core.risk.api;

import com.hope.trading.trading_core.risk.application.RiskProfileCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/risk-profiles")
@RequiredArgsConstructor
public class RiskProfileCatalogController {
    private final RiskProfileCatalogService service;

    @GetMapping("/eligible")
    public ResponseEntity<List<RiskProfileCatalogResponse>> eligible() {
        return ResponseEntity.ok(service.eligiblePlatformProfiles());
    }
}
