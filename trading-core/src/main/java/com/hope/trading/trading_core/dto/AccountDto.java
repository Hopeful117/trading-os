package com.hope.trading.trading_core.dto;

import com.hope.trading.trading_core.broker.dto.AccountBalanceDto;
import com.hope.trading.trading_core.shared.domain.model.SettlementModel;
import com.hope.trading.trading_core.model.AccountValuationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountDto {
    private UUID accountId;

    private UUID brokerAccountId;

    private String name;

    private String baseCurrency;

    private SettlementModel settlementModel;

    private AccountBalanceDto balances;

    private BigDecimal equity;

    private String synchronizationStatus;

    private String financialDataStatus;

    private String riskEvidenceStatus;

    private String tradingReadinessStatus;

    private String snapshotCompleteness;

    private String snapshotFreshness;

    private String positionAvailability;

    private String orderAvailability;

    private String pnlAvailability;

    private String dealAvailability;

    private List<String> synchronizationReasons;

    private BigDecimal derivedEquityCandidate;

    private String derivedEquityCurrency;

    private String derivedEquityProvenance;

    private String derivedEquityCalculationVersion;

    private String derivedEquityHypothesis;

    private String derivedEquityConsistencyStatus;

    private String derivedEquityValidationStatus;

    private List<String> derivedEquityReasons;

    private boolean diagnosticOnly;

    private boolean riskEvidenceIncomplete;

    private boolean tradingNotAuthorized;

    private BigDecimal peakEquity;

    private BigDecimal realizedBalance;

    private AccountValuationStatus valuationStatus;

    private java.time.Instant valuedAt;

    private java.time.Instant marketDataTimestamp;

    private UUID rulesId;

    private UUID userId;

    private UUID riskProfileId;

    private String riskProfileSemanticVersion;

    private UUID tradePlanningProfileId;

    private Long tradePlanningProfileVersion;

}
