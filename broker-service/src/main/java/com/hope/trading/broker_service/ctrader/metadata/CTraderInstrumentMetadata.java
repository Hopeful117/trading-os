package com.hope.trading.broker_service.ctrader.metadata;

import java.math.BigDecimal;

/** Read-only provider facts. This type deliberately contains no Trading OS product classification. */
public record CTraderInstrumentMetadata(
        CTraderAccountFacts account,
        CTraderSymbolFacts symbol) {

    public CTraderInstrumentMetadata {
        if (account == null || symbol == null) {
            throw new IllegalArgumentException("cTrader metadata facts are required");
        }
    }

    public record CTraderAccountFacts(
            String externalAccountId,
            Boolean providerLive,
            String providerAccountType,
            String accountCurrency,
            BigDecimal balance,
            BigDecimal equity,
            BigDecimal margin,
            BigDecimal freeMargin,
            Long leverageInCents,
            Long maxLeverageInCents,
            Integer moneyDigits,
            String marginCalculationType) {
    }

    /**
     * Numeric commission and swap values retain cTrader's raw protocol units.
     * Volume and lot-size values also retain provider units; distances retain
     * the provider unit named by distanceSetIn.
     */
    public record CTraderSymbolFacts(
            long symbolId,
            String providerSymbol,
            String description,
            Long categoryId,
            String baseAsset,
            String quoteAsset,
            Integer digits,
            Integer pipPosition,
            Long lotSize,
            Long minVolume,
            Long maxVolume,
            Long stepVolume,
            Long commission,
            String commissionType,
            Long minCommission,
            String minCommissionType,
            Long swapLong,
            Long swapShort,
            String swapCalculationType,
            Integer tripleSwapDay,
            String tradingMode,
            String measurementUnits,
            Long stopLossDistance,
            Long takeProfitDistance,
            String distanceSetIn) {

        public CTraderSymbolFacts {
            if (symbolId <= 0 || providerSymbol == null || providerSymbol.isBlank()) {
                throw new IllegalArgumentException("cTrader symbol identity is required");
            }
        }
    }
}
