package com.hope.trading.trading_core.helper;

import com.hope.trading.trading_core.broker.dto.AccountBalanceDto;
import com.hope.trading.trading_core.dto.AccountDto;
import com.hope.trading.trading_core.broker.dto.BrokerAccountDto;
import com.hope.trading.trading_core.model.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class AccountMapper {



        public AccountDto toDto(Account account) {
            boolean snapshotIncomplete = snapshotIncomplete(account);

            return AccountDto.builder()
                    .accountId(account.getAccountId())
                    .brokerAccountId(account.getBrokerAccountId())
                    .name(account.getName())
                    .baseCurrency(account.getBaseCurrency())
                    .settlementModel(account.getSettlementModel())
                    .equity(exposedEquity(account))
                    .peakEquity(account.getPeakEquity())
                    .realizedBalance(account.getRealizedBalance())
                    .valuationStatus(account.getValuationStatus())
                    .valuedAt(account.getValuedAt())
                    .marketDataTimestamp(account.getMarketDataTimestamp())
                    .synchronizationStatus(account.getBrokerSnapshotCompleteness() == null
                            ? null : "ACCOUNT_SYNCHRONIZED")
                    .financialDataStatus(account.getBrokerSnapshotCompleteness() == null
                            ? null : ("COMPLETE".equals(account.getBrokerSnapshotCompleteness())
                            && !snapshotIncomplete
                            ? "FINANCIAL_DATA_COMPLETE" : "FINANCIAL_DATA_PARTIAL"))
                    .riskEvidenceStatus(account.getBrokerSnapshotCompleteness() == null
                            ? null : ("COMPLETE".equals(account.getBrokerSnapshotCompleteness())
                            && !snapshotIncomplete
                            ? "RISK_EVIDENCE_PENDING" : "RISK_EVIDENCE_INCOMPLETE"))
                    .tradingReadinessStatus("TRADING_NOT_AUTHORIZED")
                    .snapshotCompleteness(account.getBrokerSnapshotCompleteness())
                    .snapshotFreshness(account.getBrokerSnapshotFreshness())
                    .positionAvailability(account.getBrokerSnapshotPositionAvailability())
                    .orderAvailability(account.getBrokerSnapshotOrderAvailability())
                    .pnlAvailability(account.getBrokerSnapshotPnlAvailability())
                    .dealAvailability(account.getBrokerSnapshotDealAvailability())
                    .synchronizationReasons(splitReasons(account.getBrokerSnapshotUnavailabilityReasons()))
                    .derivedEquityCandidate(account.getBrokerDerivedEquityCandidate())
                    .derivedEquityCurrency(account.getBrokerDerivedEquityCurrency())
                    .derivedEquityProvenance(account.getBrokerDerivedEquityProvenance())
                    .derivedEquityCalculationVersion(account.getBrokerDerivedEquityCalculationVersion())
                    .derivedEquityHypothesis(account.getBrokerDerivedEquityHypothesis())
                    .derivedEquityConsistencyStatus(account.getBrokerDerivedEquityConsistencyStatus())
                    .derivedEquityValidationStatus(account.getBrokerDerivedEquityValidationStatus())
                    .derivedEquityReasons(splitReasons(account.getBrokerDerivedEquityReasons()))
                    .diagnosticOnly(account.getBrokerSnapshotCompleteness() != null
                            && snapshotIncomplete)
                    .riskEvidenceIncomplete(account.getBrokerSnapshotCompleteness() != null
                            && snapshotIncomplete)
                    .tradingNotAuthorized(true)
                    .rulesId(
                            account.getRules() != null
                                    ? account.getRules().getRulesId()
                                    : null
                    )
                    .userId(
                            account.getUser() != null
                                    ? account.getUser().getUserId()
                                    : null
                    )
                    .balances(
                            toAccountBalanceDto(account.getBalances())


                    )

                    .build();
        }

        private java.math.BigDecimal exposedEquity(Account account) {
            String completeness = account.getBrokerSnapshotCompleteness();
            return completeness == null || ("COMPLETE".equals(completeness) && !snapshotIncomplete(account))
                    ? account.getEquity()
                    : null;
        }

        private boolean snapshotIncomplete(Account account) {
            return !splitReasons(account.getBrokerSnapshotUnavailabilityReasons()).isEmpty();
        }

        private List<String> splitReasons(String value) {
            if (value == null || value.isBlank()) return List.of();
            return List.of(value.split(","));
        }







        public Account toEntity(BrokerAccountDto brokerAccountDto) {

            return Account.builder()
                    .broker(brokerAccountDto.getBroker())
                    .baseCurrency(brokerAccountDto.getBaseCurrency())
                    .settlementModel(com.hope.trading.trading_core.shared.domain.model.SettlementModel.UNKNOWN)
                    .name(brokerAccountDto.getAccountName())
                    .balances(toAccountBalance(brokerAccountDto.getBalances()))
                    .build();
        }

        public List<AccountBalance> toAccountBalance(AccountBalanceDto accountBalanceDto){
                return accountBalanceDto.getBalances()
                        .entrySet()
                        .stream()
                        .map(entry -> {
                                AccountBalance accountBalance = new AccountBalance();
                                accountBalance.setAsset(entry.getKey());
                                accountBalance.setAmount(entry.getValue());
                                return accountBalance;
                        })
                        .collect(Collectors.toCollection(ArrayList::new));
        }



    public AccountBalanceDto toAccountBalanceDto(List<AccountBalance> accountBalances) {

        return AccountBalanceDto.builder()
                .balances(
                        accountBalances.stream()
                                .collect(Collectors.toMap(
                                        AccountBalance::getAsset,
                                        AccountBalance::getAmount
                                ))
                )
                .build();
    }
        }
