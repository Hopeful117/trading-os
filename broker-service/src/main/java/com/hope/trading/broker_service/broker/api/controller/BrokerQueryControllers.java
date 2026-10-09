package com.hope.trading.broker_service.broker.api.controller;

import com.hope.trading.broker_service.broker.application.service.BrokerOperationServices.*;
import com.hope.trading.broker_service.broker.domain.model.BrokerModels.*;
import com.hope.trading.broker_service.security.BrokerPrincipal;
import com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.BrokerAuthorizationException;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

public final class BrokerQueryControllers {
    private BrokerQueryControllers() {
    }

    @RestController
    @RequestMapping("/internal/v1/broker-accounts")
    public static final class AccountController {
        private final GetAccountService service;

        public AccountController(GetAccountService service) {
            this.service = service;
        }

        @GetMapping("/{id}")
        public ResponseEntity<AccountSnapshot> get(@PathVariable UUID id) {
            return ResponseEntity.ok(service.get(id));
        }
    }

    @RestController
    @RequestMapping("/internal/v1/broker-accounts")
    public static final class PositionController {
        private final GetPositionsService service;

        public PositionController(GetPositionsService service) {
            this.service = service;
        }

        @GetMapping("/{id}/positions")
        public ResponseEntity<List<PositionSnapshot>> get(@PathVariable UUID id) {
            return ResponseEntity.ok(service.get(id));
        }
    }

    @RestController
    @RequestMapping("/internal/v1/broker-accounts")
    public static final class OrderController {
        private final GetOrdersService service;

        public OrderController(GetOrdersService service) {
            this.service = service;
        }

        @GetMapping("/{id}/orders")
        public ResponseEntity<List<OrderSnapshot>> get(@PathVariable UUID id) {
            return ResponseEntity.ok(service.get(id));
        }
    }

    @RestController
    @RequestMapping("/internal/v1/broker-accounts")
    public static final class RiskSnapshotController {
        private final GetRiskSnapshotService service;

        public RiskSnapshotController(GetRiskSnapshotService service) {
            this.service = service;
        }

        @GetMapping("/{id}/risk-snapshot")
        public ResponseEntity<RiskSnapshot> get(
                @PathVariable UUID id,
                @RequestParam java.time.Instant from,
                @RequestParam java.time.Instant to,
                @AuthenticationPrincipal BrokerPrincipal principal) {
            requireDelegatedActor(principal);
            return ResponseEntity.ok(service.get(principal.userId(), id, from, to));
        }
    }

    @RestController
    @RequestMapping("/internal/v1/broker-accounts")
    public static final class TechnicalCapabilitiesController {
        private final GetTechnicalCapabilitiesService service;

        public TechnicalCapabilitiesController(GetTechnicalCapabilitiesService service) {
            this.service = service;
        }

        @GetMapping("/{id}/capabilities")
        public ResponseEntity<TechnicalCapabilities> get(
                @PathVariable UUID id,
                @RequestParam String instrument,
                @AuthenticationPrincipal BrokerPrincipal principal) {
            requireDelegatedActor(principal);
            return ResponseEntity.ok(service.get(principal.userId(), id, instrument));
        }
    }

    @RestController
    @RequestMapping("/internal/v1/broker-providers")
    public static final class ProviderTechnicalCapabilitiesController {
        private final GetProviderTechnicalCapabilitiesService service;

        public ProviderTechnicalCapabilitiesController(GetProviderTechnicalCapabilitiesService service) {
            this.service = service;
        }

        @GetMapping("/{provider}/capabilities")
        public ResponseEntity<TechnicalCapabilities> get(
                @PathVariable com.hope.trading.broker_service.connection.domain.BrokerProviderId provider,
                @RequestParam UUID brokerAccountId,
                @RequestParam String instrument,
                @AuthenticationPrincipal BrokerPrincipal principal) {
            requireDelegatedActor(principal);
            return ResponseEntity.ok(service.get(provider, brokerAccountId, instrument));
        }
    }

    @RestController
    @RequestMapping("/internal/v1/broker-accounts")
    public static final class MarginPreviewController {
        private final PreviewMarginService service;

        public MarginPreviewController(PreviewMarginService service) {
            this.service = service;
        }

        @PostMapping("/{id}/margin-preview")
        public ResponseEntity<MarginPreview> preview(
                @PathVariable UUID id,
                @RequestBody MarginPreviewRequest request,
                @AuthenticationPrincipal BrokerPrincipal principal) {
            requireDelegatedActor(principal);
            if (!id.equals(request.brokerAccountId())) {
                throw new IllegalArgumentException("Broker account path does not match request");
            }
            return ResponseEntity.ok(service.preview(request, principal.userId()));
        }
    }

    @RestController
    @RequestMapping("/internal/v1/broker-providers")
    public static final class ProviderMarginPreviewController {
        private final PreviewProviderMarginService service;

        public ProviderMarginPreviewController(PreviewProviderMarginService service) {
            this.service = service;
        }

        @PostMapping("/{provider}/margin-preview")
        public ResponseEntity<MarginPreview> preview(
                @PathVariable com.hope.trading.broker_service.connection.domain.BrokerProviderId provider,
                @RequestBody MarginPreviewRequest request,
                @AuthenticationPrincipal BrokerPrincipal principal) {
            requireDelegatedActor(principal);
            return ResponseEntity.ok(service.preview(provider, request));
        }
    }

    private static void requireDelegatedActor(BrokerPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new BrokerAuthorizationException("Delegated actor is required");
        }
    }
}
