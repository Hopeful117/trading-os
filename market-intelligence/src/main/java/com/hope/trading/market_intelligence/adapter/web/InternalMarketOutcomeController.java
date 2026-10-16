package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.outcome.MarketOutcomeEvaluationRequest;
import com.hope.trading.market_intelligence.application.outcome.MarketOutcomeEvaluationService;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcome;
import com.hope.trading.market_intelligence.domain.outcome.OutcomeHorizon;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/market-outcomes")
public class InternalMarketOutcomeController {
    private final MarketOutcomeEvaluationService service;

    public InternalMarketOutcomeController(MarketOutcomeEvaluationService service) {
        this.service = service;
    }

    @PostMapping("/evaluate")
    public ResponseEntity<ObservedMarketOutcome> evaluate(
            @RequestBody MarketOutcomeEvaluationRequestDto request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Outcome request is required");
            }
            if (request.marketId() == null || request.evaluationCutoff() == null || request.horizon() == null) {
                throw new IllegalArgumentException("marketId, evaluationCutoff and horizon are required");
            }
            return ResponseEntity.ok(service.evaluate(new MarketOutcomeEvaluationRequest(
                    request.marketId(), request.evaluationCutoff(),
                    new OutcomeHorizon(request.horizon()), request.analysisExecutionId())));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
}
