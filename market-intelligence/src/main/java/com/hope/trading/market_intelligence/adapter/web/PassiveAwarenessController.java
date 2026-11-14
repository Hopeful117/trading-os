package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.passive.PassiveAwarenessProjectionService;
import com.hope.trading.market_intelligence.application.passive.PassiveAwarenessQuery;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/passive-awareness")
public final class PassiveAwarenessController {
    private final PassiveAwarenessProjectionService service;

    public PassiveAwarenessController(PassiveAwarenessProjectionService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<PassiveAwarenessPageResponse> search(
            @RequestParam(required = false) String instrument,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String productType,
            @RequestParam(defaultValue = "false") boolean currentOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = service.search(new PassiveAwarenessQuery(
                instrument, provider, productType, currentOnly, page, size));
        return ResponseEntity.ok(new PassiveAwarenessPageResponse(
                result.items().stream().map(PassiveAwarenessResponse::from).toList(),
                result.page(), result.size(), result.total(), result.truncated()));
    }
}
