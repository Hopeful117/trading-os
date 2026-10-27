package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.port.TradingOpportunityRepository;
import com.hope.trading.market_intelligence.application.port.MarketThesisRepository;
import com.hope.trading.market_intelligence.domain.opportunity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Public trader-facing opportunity API (STORY-0019). Lives under the
 * service-owned public prefix so the Gateway routes it by the existing
 * no-rewrite convention.
 */
@RestController
@RequestMapping("/api/v1/opportunities")
public final class OpportunityController {
    private final TradingOpportunityRepository repository;
    private final MarketThesisRepository theses;

    @Autowired
    public OpportunityController(TradingOpportunityRepository repository,
                                 MarketThesisRepository theses) {
        this.repository = repository;
        this.theses = theses;
    }

    public OpportunityController(TradingOpportunityRepository repository) {
        this(repository, null);
    }

    @GetMapping
    public ResponseEntity<OpportunityPageResponse> search(
            @RequestParam(required = false) String instrument,
            @RequestParam(required = false) String timeframe,
            @RequestParam(required = false) OpportunityStatus status,
            @RequestParam(required = false) OpportunityType type,
            @RequestParam(defaultValue = "false") boolean activeOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        if (page < 0 || size < 1 || size > 200) {
            throw new IllegalArgumentException("Invalid pagination");
        }
        Comparator<TradingOpportunity> comparator = comparator(sort);
        if ("desc".equalsIgnoreCase(direction)) comparator = comparator.reversed();
        List<TradingOpportunity> filtered = repository.findAllLatest().stream()
                .filter(item -> instrument == null
                        || item.instrument().equalsIgnoreCase(instrument))
                .filter(item -> timeframe == null
                        || item.timeframe().equalsIgnoreCase(timeframe))
                .filter(item -> status == null || item.status() == status)
                .filter(item -> type == null || item.type() == type)
                .filter(item -> !activeOnly || item.status() == OpportunityStatus.ACTIVE)
                .sorted(comparator.thenComparing(item -> item.id().value()))
                .toList();
        int from = Math.min(page * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        List<OpportunityResponse> items = filtered.subList(from, to).stream()
                .map(value -> response(value, false)).toList();
        return ResponseEntity.ok(new OpportunityPageResponse(
                items, page, size, filtered.size()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OpportunityResponse> latest(@PathVariable UUID id) {
        return repository.findLatest(new OpportunityId(id))
                .map(value -> response(value, true)).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/active")
    public ResponseEntity<List<OpportunityResponse>> active() {
        return ResponseEntity.ok(repository.findActive().stream()
                .map(value -> response(value, false)).toList());
    }

    @GetMapping("/history/{id}")
    public ResponseEntity<List<OpportunityResponse>> history(@PathVariable UUID id) {
        List<OpportunityResponse> history = repository.findHistory(
                new OpportunityId(id)).stream().map(value -> response(value, false)).toList();
        return history.isEmpty() ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(history);
    }

    private OpportunityResponse response(TradingOpportunity value) {
        return response(value, true);
    }

    private OpportunityResponse response(TradingOpportunity value, boolean includeThesis) {
        MarketThesisResponse thesis = !includeThesis || theses == null ? null : value.marketThesis()
                .flatMap(reference -> theses.findByIdAndVersion(
                        reference.thesisId(), reference.thesisVersion()))
                .map(MarketThesisResponse::from)
                .orElse(null);
        return OpportunityResponse.from(value, thesis);
    }

    private Comparator<TradingOpportunity> comparator(String sort) {
        return switch (sort) {
            case "score" -> Comparator.comparing(TradingOpportunity::score);
            case "instrument" -> Comparator.comparing(TradingOpportunity::instrument);
            case "evaluatedAt" -> Comparator.comparing(TradingOpportunity::evaluatedAt);
            case "createdAt" -> Comparator.comparing(TradingOpportunity::createdAt);
            default -> throw new IllegalArgumentException("Unsupported sort: " + sort);
        };
    }
}
