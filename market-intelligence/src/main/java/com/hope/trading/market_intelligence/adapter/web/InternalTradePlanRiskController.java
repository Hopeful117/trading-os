package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.tradeplan.RiskValidationAcknowledgment;
import com.hope.trading.market_intelligence.application.tradeplan.TradePlanRiskHandoffService;
import com.hope.trading.market_intelligence.application.tradeplan.TradePlanRiskSnapshot;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanId;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanVersion;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/trade-plans/{tradePlanId}/versions/{version}")
public final class InternalTradePlanRiskController {
    private final TradePlanRiskHandoffService service;

    public InternalTradePlanRiskController(TradePlanRiskHandoffService service) {
        this.service = service;
    }

    @GetMapping("/risk-validation-snapshot")
    public ResponseEntity<TradePlanRiskSnapshot> snapshot(
            @PathVariable UUID tradePlanId, @PathVariable long version) {
        return ResponseEntity.ok(service.loadAcceptedSnapshot(
                new TradePlanId(tradePlanId), new TradePlanVersion(version)));
    }

    @PostMapping("/risk-validation-acknowledgments")
    public ResponseEntity<RiskValidationAcknowledgment> acknowledge(
            @PathVariable UUID tradePlanId, @PathVariable long version,
            @Valid @RequestBody RiskValidationAcknowledgmentRequest request) {
        return ResponseEntity.ok(service.acknowledgeApprovedEvaluation(
                new TradePlanId(tradePlanId), new TradePlanVersion(version),
                request.evaluationId(), request.decision(), request.evaluatedAt()));
    }
}
