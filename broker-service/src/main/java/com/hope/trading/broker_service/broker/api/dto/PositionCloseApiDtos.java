package com.hope.trading.broker_service.broker.api.dto;

import com.hope.trading.broker_service.broker.domain.model.BrokerModels.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public final class PositionCloseApiDtos { private PositionCloseApiDtos() {}

    public record ResolveTargetApiRequest(@NotNull UUID brokerAccountId, @NotBlank @Size(max=200) String brokerPositionReference) {
        public ResolveTargetRequest toModel() { return new ResolveTargetRequest(brokerAccountId, brokerPositionReference); }
    }

    public record ResolvedTargetApiResponse(@NotNull UUID brokerAccountId, @NotBlank @Size(max=200) String resolvedMutationScope) {
        public static ResolvedTargetApiResponse from(ResolvedPositionCloseTarget target) {
            return new ResolvedTargetApiResponse(target.brokerAccountId(), target.resolvedMutationScope());
        }
    }

    public record ExecuteCloseApiRequest(@NotNull UUID brokerAccountId, @NotBlank @Size(max=200) String resolvedMutationScope,
                                         BigDecimal closeQuantity, @NotBlank @Size(max=200) String idempotencyKey) {
        public ExecuteCloseApiRequest(UUID brokerAccountId, String resolvedMutationScope, String idempotencyKey) {
            this(brokerAccountId, resolvedMutationScope, null, idempotencyKey);
        }
        public ExecuteCloseRequest toModel() { return new ExecuteCloseRequest(brokerAccountId, resolvedMutationScope, closeQuantity, idempotencyKey); }
    }

    public record BrokerCloseApiResponse(String outcome, String externalOrderId, String correlationId, String status, String reasonCode) {
        public static BrokerCloseApiResponse from(CloseResult r) {
            return switch (r) {
                case CloseAcknowledged a -> new BrokerCloseApiResponse("ACKNOWLEDGED", a.externalOrderId(), a.correlationId(), "ACKNOWLEDGED", null);
                case CloseRejected x -> new BrokerCloseApiResponse("REJECTED", x.externalOrderId(), null, "REJECTED", x.reasonCode());
                case CloseUnknown x -> new BrokerCloseApiResponse("UNKNOWN", null, null, "UNKNOWN", x.reasonCode());
            };
        }
    }

    public record ReconcileCloseApiRequest(@NotNull UUID brokerAccountId, @NotBlank @Size(max=200) String resolvedMutationScope, @NotBlank @Size(max=200) String idempotencyKey) {
        public ReconcileCloseRequest toModel() { return new ReconcileCloseRequest(brokerAccountId, resolvedMutationScope, idempotencyKey); }
    }

    public record ReconcileCloseApiResponse(String outcome, String reconciliationResult, java.math.BigDecimal remainingQuantity) {
        public static ReconcileCloseApiResponse from(ReconciliationCloseResult r) {
            return switch (r) {
                case ExposureConfirmedAbsent ignored -> new ReconcileCloseApiResponse("EXPOSURE_CONFIRMED_ABSENT", "EXPOSURE_CONFIRMED_ABSENT", null);
                case ExposureConfirmedPartial x -> new ReconcileCloseApiResponse("EXPOSURE_CONFIRMED_PARTIAL", "EXPOSURE_CONFIRMED_PARTIAL", x.remainingQuantity());
                case CommandConfirmedNotExecuted ignored -> new ReconcileCloseApiResponse("COMMAND_CONFIRMED_NOT_EXECUTED", "COMMAND_CONFIRMED_NOT_EXECUTED", null);
                case Inconclusive x -> new ReconcileCloseApiResponse("INCONCLUSIVE", "INCONCLUSIVE: " + x.reasonCode(), null);
            };
        }
    }
}
