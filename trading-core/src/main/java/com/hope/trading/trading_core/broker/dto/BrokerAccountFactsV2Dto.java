package com.hope.trading.trading_core.broker.dto;

import java.math.BigDecimal;

public record BrokerAccountFactsV2Dto(String valuationAsset, BigDecimal balance,
                                      BigDecimal equity, BigDecimal usedMargin,
                                      BigDecimal freeMargin,
                                      AccountPositionModel positionModel,
                                      MarginCalculationModel marginCalculationModel,
                                      Integer monetaryScale,
                                      BigDecimal leverage) {
    public BrokerAccountFactsV2Dto {
        positionModel = positionModel == null ? AccountPositionModel.UNKNOWN : positionModel;
        marginCalculationModel = marginCalculationModel == null ? MarginCalculationModel.UNKNOWN : marginCalculationModel;
    }

    public BrokerAccountFactsV2Dto(String valuationAsset, BigDecimal balance,
                                   BigDecimal equity, BigDecimal usedMargin,
                                   BigDecimal freeMargin) {
        this(valuationAsset, balance, equity, usedMargin, freeMargin,
                AccountPositionModel.UNKNOWN, MarginCalculationModel.UNKNOWN, null, null);
    }
}
