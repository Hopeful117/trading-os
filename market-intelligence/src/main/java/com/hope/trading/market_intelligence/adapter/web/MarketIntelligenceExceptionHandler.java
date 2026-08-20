package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.scan.ActiveScanException;
import com.hope.trading.market_intelligence.application.execution.AnalysisExecutionNotFoundException;
import com.hope.trading.market_intelligence.application.scope.ActiveScanScopeResolutionException;
import com.hope.trading.market_intelligence.application.tradeplan.TradePlanRiskHandoffException;
import com.hope.trading.market_intelligence.domain.execution.IllegalExecutionTransitionException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class MarketIntelligenceExceptionHandler {
    @ExceptionHandler(TradePlanRiskHandoffException.class)
    ResponseEntity<Map<String, String>> tradePlanRiskHandoff(
            TradePlanRiskHandoffException exception) {
        return ResponseEntity.status(exception.status()).body(Map.of(
                "code", exception.code(), "error", exception.getMessage()));
    }

    @ExceptionHandler(AnalysisExecutionNotFoundException.class)
    ResponseEntity<Map<String, String>> notFound(AnalysisExecutionNotFoundException exception) {
        return ResponseEntity.status(404).body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(ActiveScanScopeResolutionException.class)
    ResponseEntity<Map<String, String>> activeScanScopeResolution(
            ActiveScanScopeResolutionException exception) {
        return ResponseEntity.status(exception.status()).body(Map.of(
                "code", exception.code(), "error", exception.getMessage()));
    }

    @ExceptionHandler(ActiveScanException.class)
    ResponseEntity<Map<String, String>> activeScan(ActiveScanException exception) {
        return ResponseEntity.status(exception.status()).body(Map.of(
                "code", exception.code(), "error", exception.getMessage()));
    }

    @ExceptionHandler({IllegalExecutionTransitionException.class, IllegalArgumentException.class})
    ResponseEntity<Map<String, String>> conflict(RuntimeException exception) {
        return ResponseEntity.status(409).body(Map.of("error", exception.getMessage()));
    }
}
