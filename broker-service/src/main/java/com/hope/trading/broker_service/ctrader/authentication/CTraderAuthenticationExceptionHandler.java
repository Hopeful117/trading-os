package com.hope.trading.broker_service.ctrader.authentication;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.LinkedHashMap;

@RestControllerAdvice
public class CTraderAuthenticationExceptionHandler {
    @ExceptionHandler(CTraderAuthenticationException.class)
    public ResponseEntity<Map<String, String>> handle(CTraderAuthenticationException exception) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("message", exception.getMessage());
        CTraderAuthenticationException.ProviderDiagnostic diagnostic = exception.diagnostic();
        if (diagnostic != null) {
            put(body, "stage", diagnostic.stage());
            put(body, "requestPayloadType", diagnostic.requestPayloadType());
            put(body, "requestMessage", diagnostic.requestMessage());
            put(body, "responsePayloadType", diagnostic.responsePayloadType());
            put(body, "responseMessage", diagnostic.responseMessage());
            put(body, "providerErrorCode", diagnostic.providerErrorCode());
            put(body, "providerReason", safeText(diagnostic.providerReason()));
            put(body, "providerDescription", safeText(diagnostic.providerDescription()));
            put(body, "correlationId", diagnostic.correlationId());
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    private void put(Map<String, String> body, String key, Object value) {
        if (value != null) body.put(key, value.toString());
    }

    private String safeText(String value) {
        if (value == null || value.isBlank()) return null;
        String redacted = value.replaceAll(
                "(?i)(access[_ -]?token|refresh[_ -]?token|client[_ -]?secret|authorization)"
                        + "\\s*[:=]\\s*(?:Bearer\\s+)?[^\\s,;]+", "$1=[REDACTED]");
        redacted = redacted.replaceAll("[\\p{Cntrl}]", " ")
                .replaceAll("\\s+", " ").trim();
        return redacted.length() <= 256 ? redacted : redacted.substring(0, 256);
    }

    @ExceptionHandler(CTraderSnapshotDeferredException.class)
    public ResponseEntity<Map<String, String>> handleSnapshotDeferred(
            CTraderSnapshotDeferredException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("code", "BROKER_SNAPSHOT_DEFERRED", "message", exception.getMessage()));
    }
}
