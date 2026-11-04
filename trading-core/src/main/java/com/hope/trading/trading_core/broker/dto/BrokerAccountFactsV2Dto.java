package com.hope.trading.trading_core.broker.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record BrokerAccountFactsV2Dto(String valuationAsset, BigDecimal balance,
                                      BigDecimal equity, BigDecimal usedMargin,
                                      BigDecimal freeMargin,
                                      AccountPositionModel positionModel,
                                      MarginCalculationModel marginCalculationModel,
                                      Integer monetaryScale,
                                      BigDecimal leverage,
                                      EquityFactDto equityFact) {
    public BrokerAccountFactsV2Dto {
        positionModel = positionModel == null ? AccountPositionModel.UNKNOWN : positionModel;
        marginCalculationModel = marginCalculationModel == null ? MarginCalculationModel.UNKNOWN : marginCalculationModel;
        equityFact = equityFact == null ? EquityFactDto.unavailable("EQUITY_PROVENANCE_UNAVAILABLE") : equityFact;
    }

    public BrokerAccountFactsV2Dto(String valuationAsset, BigDecimal balance,
                                   BigDecimal equity, BigDecimal usedMargin,
                                   BigDecimal freeMargin) {
        this(valuationAsset, balance, equity, usedMargin, freeMargin,
                AccountPositionModel.UNKNOWN, MarginCalculationModel.UNKNOWN, null, null,
                EquityFactDto.unavailable("EQUITY_PROVENANCE_UNAVAILABLE"));
    }

    public BrokerAccountFactsV2Dto(String valuationAsset, BigDecimal balance,
                                   BigDecimal equity, BigDecimal usedMargin,
                                   BigDecimal freeMargin, AccountPositionModel positionModel,
                                   MarginCalculationModel marginCalculationModel,
                                   Integer monetaryScale, BigDecimal leverage) {
        this(valuationAsset, balance, equity, usedMargin, freeMargin, positionModel,
                marginCalculationModel, monetaryScale, leverage,
                EquityFactDto.unavailable("EQUITY_PROVENANCE_UNAVAILABLE"));
    }

    public record EquityFactDto(BigDecimal value, String currency, String provenance,
                                String availability, Instant observedAt, Instant capturedAt,
                                boolean fresh, boolean complete, boolean coherent, String source,
                                String resolutionMethod, List<String> reasons) {
        public EquityFactDto {
            reasons = List.copyOf(reasons == null ? List.of() : reasons);
            if (availability == null || provenance == null) {
                throw new IllegalArgumentException("equity availability and provenance are required");
            }
            if ("UNAVAILABLE".equals(availability)
                    && (value != null || currency != null || !"UNAVAILABLE".equals(provenance))) {
                throw new IllegalArgumentException("unavailable equity cannot contain a usable value");
            }
            if ("AVAILABLE".equals(availability)
                    && (value == null || value.signum() < 0 || currency == null || currency.isBlank()
                    || observedAt == null || capturedAt == null || source == null || source.isBlank()
                    || resolutionMethod == null || resolutionMethod.isBlank()
                    || "UNAVAILABLE".equals(provenance))) {
                throw new IllegalArgumentException("available equity facts are incomplete");
            }
        }

        public static EquityFactDto unavailable(String reason) {
            return new EquityFactDto(null, null, "UNAVAILABLE", "UNAVAILABLE", null, null,
                    false, false, false, "UNKNOWN", "UNAVAILABLE",
                    reason == null || reason.isBlank() ? List.of("EQUITY_UNAVAILABLE") : List.of(reason));
        }
    }
}
