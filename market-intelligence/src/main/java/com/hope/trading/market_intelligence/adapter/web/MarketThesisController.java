package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.thesis.MarketThesisQueryService;
import com.hope.trading.market_intelligence.security.MiUserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/intelligence/market-theses")
public class MarketThesisController {
    private final MarketThesisQueryService theses;

    public MarketThesisController(MarketThesisQueryService theses) {
        this.theses = theses;
    }

    @GetMapping("/{thesisId}/versions/{version}")
    public ResponseEntity<MarketThesisResponse> find(
            Authentication authentication,
            @PathVariable UUID thesisId,
            @PathVariable long version) {
        requireActor(authentication);
        return theses.find(thesisId, version)
                .map(value -> ResponseEntity.ok(MarketThesisResponse.from(value)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/latest")
    public ResponseEntity<MarketThesisResponse> latest(
            Authentication authentication,
            @RequestParam UUID marketId,
            @RequestParam String instrument,
            @RequestParam String direction) {
        requireActor(authentication);
        return theses.findLatest(marketId, instrument, direction)
                .map(value -> ResponseEntity.ok(MarketThesisResponse.from(value)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private void requireActor(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof MiUserPrincipal)) {
            throw new IllegalArgumentException("Authenticated actor context is required");
        }
    }
}
