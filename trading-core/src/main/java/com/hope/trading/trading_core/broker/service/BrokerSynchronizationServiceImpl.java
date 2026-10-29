package com.hope.trading.trading_core.broker.service;

import com.hope.trading.trading_core.broker.apiClient.BrokerApiClient;
import com.hope.trading.trading_core.broker.apiClient.BrokerAccountSnapshotV2Client;
import com.hope.trading.trading_core.broker.dto.BrokerAccountDto;
import com.hope.trading.trading_core.broker.dto.BrokerAccountSnapshotV2Dto;
import com.hope.trading.trading_core.broker.dto.BrokerDealSnapshotV2Dto;
import com.hope.trading.trading_core.broker.dto.BrokerPositionSnapshotV2Dto;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.exception.EntityNotFoundException;
import com.hope.trading.trading_core.helper.AccountMapper;
import com.hope.trading.trading_core.helper.TradeStatus;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.AccountBalance;
import com.hope.trading.trading_core.model.Trade;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.repository.TradeRepository;
import com.hope.trading.trading_core.repository.UserRepository;
import com.hope.trading.trading_core.service.TradingCalculatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BrokerSynchronizationServiceImpl implements BrokerSynchronizationService {
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TradeRepository tradeRepository;
    private final TradingCalculatorService tradingCalculatorService;
    private final BrokerApiClient brokerApiClient;
    private final BrokerAccountSnapshotV2Client brokerAccountSnapshotV2Client;
    private final AccountMapper accountMapper;
    private final BrokerAccountRepository brokerAccountRepository;

    @Override
    @Transactional
    public void synchronize(String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new EntityNotFoundException("User not found"));

        BrokerAccountDto snapshot = brokerApiClient.getAccount();

        Account account = synchronizeAccount(user, snapshot);

        synchronizeBalances(account, snapshot);

        synchronizeOpenTrades(account, snapshot);

        recalculateAccount(account);

        accountRepository.save(account);
    }

    @Override
    @Transactional
    public void synchronize(UUID ownerId, UUID brokerAccountId) {
        if (ownerId == null || brokerAccountId == null) {
            throw new IllegalArgumentException("ownerId and brokerAccountId are required");
        }

        Account account = accountRepository.findByBrokerAccountId(brokerAccountId)
                .orElseThrow(() -> new EntityNotFoundException("Account mapping not found"));
        if (account.getUser() == null || !ownerId.equals(account.getUser().getUserId())) {
            throw new EntityNotFoundException("Account mapping not found");
        }

        var brokerAccount = brokerAccountRepository.findByIdAndOwnerId(brokerAccountId, ownerId)
                .orElseThrow(() -> new EntityNotFoundException("Broker account mapping not found"));
         BrokerAccountSnapshotV2Dto snapshot = brokerAccountSnapshotV2Client.getAccountSnapshot(brokerAccountId);
         validateSnapshot(brokerAccount.providerRegistrationReference(), brokerAccountId, snapshot);
         if (!"COMPLETE".equals(snapshot.completeness()) || !"FRESH".equals(snapshot.freshness())) {
             throw new IllegalStateException("Broker account snapshot is not complete and fresh");
         }
        if (account.getBroker() != null
                && !brokerAccount.providerRegistrationReference().toString().equals(account.getBroker())) {
            throw new IllegalStateException("Account provider metadata mismatch");
        }
        if (brokerAccount.externalAccountId() != null
                && !brokerAccount.externalAccountId().equals(snapshot.externalAccountId())) {
            throw new IllegalStateException("External broker account identity mismatch");
        }

        replaceBalances(account, snapshot.balances());
        replaceOpenPositions(account, snapshot.positions(), snapshot.deals());
        account.setMarketDataTimestamp(snapshot.observedAt());
        recalculateAccount(account);
        if (snapshot.account() != null && snapshot.account().equity() != null) {
            account.setEquity(snapshot.account().equity());
            if (account.getEquity().compareTo(account.getPeakEquity()) > 0) {
                account.setPeakEquity(account.getEquity());
            }
        }
        accountRepository.save(account);
    }

    private void validateSnapshot(UUID expectedProviderRegistrationReference, UUID brokerAccountId,
                                  BrokerAccountSnapshotV2Dto snapshot) {
        if (snapshot == null || !brokerAccountId.equals(snapshot.brokerAccountId())) {
            throw new IllegalStateException("Broker account snapshot identity mismatch");
        }
        if (!expectedProviderRegistrationReference.equals(snapshot.providerRegistrationReference())) {
            throw new IllegalStateException("Broker account snapshot provider mismatch");
        }
    }

    private Account synchronizeAccount(User user,BrokerAccountDto snapshot){

       Account account= accountRepository.findByUser_UserIdAndBroker(user.getUserId(),snapshot.getBroker()).orElse(null);
        if (account == null) {

            account = accountMapper.toEntity(snapshot);

            user.addAccount(account);

        } else {

            account.setBroker(snapshot.getBroker());
            account.setName(snapshot.getAccountName());
            account.setBaseCurrency(snapshot.getBaseCurrency());

        }

        return account;



    }
    private void synchronizeBalances(
            Account account,
            BrokerAccountDto snapshot
    ){
        replaceBalances(account, snapshot.getBalances().getBalances());
    }

    private void replaceBalances(Account account, java.util.Map<String, BigDecimal> balances) {
        account.getBalances().clear();
        balances.forEach((asset, amount) -> account.addBalance(AccountBalance.builder()
                .asset(asset)
                .amount(amount)
                .build()));
    }
    private void synchronizeOpenTrades(
            Account account,
            BrokerAccountDto snapshot
    ){
        account.getTrades().removeIf(trade -> trade.getTradeStatus() == TradeStatus.OPEN);

        snapshot.getOpenTrades()
                .forEach(position -> {

                    Trade trade = Trade.builder()
                            .symbol(position.getSymbol())
                            .type(
                                    position.getSide().equalsIgnoreCase("buy")
                                            ? TradeType.BUY
                                            : TradeType.SELL
                            )
                            .entryPrice(
                                    position.getEntryPrice() != null
                                            ? position.getEntryPrice()
                                            : position.getEntryValue()
                            )
                            .quantity(position.getQuantity())
                            .tradeStatus(TradeStatus.OPEN)
                            .build();

                    account.addTrade(trade);
                });



    }

    private void replaceOpenPositions(Account account,
                                      List<BrokerPositionSnapshotV2Dto> positions,
                                      List<BrokerDealSnapshotV2Dto> deals) {
        Map<String, Trade> tradesByReference = new HashMap<>();
        account.getTrades().stream()
                .filter(trade -> trade.getBrokerPositionReference() != null)
                .forEach(trade -> tradesByReference.putIfAbsent(trade.getBrokerPositionReference(), trade));
        Set<String> observedReferences = new HashSet<>();

        positions.forEach(position -> {
            if (position.signedQuantity() == null || position.signedQuantity().signum() == 0) {
                throw new IllegalStateException("Broker position quantity must be non-zero");
            }
            String reference = requiredPositionReference(position);
            observedReferences.add(reference);
            Trade trade = tradesByReference.get(reference);
            if (trade != null && isClosed(trade)) {
                return;
            }
            boolean imported = trade == null;
            if (trade == null) {
                trade = Trade.builder()
                        .symbol(position.instrument())
                        .type(position.signedQuantity().signum() > 0 ? TradeType.BUY : TradeType.SELL)
                        .entryPrice(position.entryPrice())
                        .quantity(position.signedQuantity().abs())
                        .tradeStatus(TradeStatus.OPEN)
                        .openedAt(position.observedAt())
                        .brokerPositionReference(reference)
                        .brokerOriginalQuantity(position.signedQuantity().abs())
                        .build();
                account.addTrade(trade);
                tradesByReference.put(reference, trade);
            } else {
                updateOpenPosition(trade, position);
            }
            List<BrokerDealSnapshotV2Dto> positionDeals = dealsForPosition(deals, reference);
            if (imported) {
                // The provider deal window may start after the position was opened.
                // Establish the local baseline without applying historical closes.
                rememberClosingDealReferences(trade, positionDeals);
            } else {
                applyClosingDeals(trade, positionDeals, true);
            }
        });

            account.getTrades().stream()
                .filter(trade -> !isClosed(trade) && trade.getBrokerPositionReference() != null)
                .filter(trade -> !observedReferences.contains(trade.getBrokerPositionReference()))
                .forEach(trade -> applyClosingDeals(trade,
                        dealsForPosition(deals, trade.getBrokerPositionReference()), false));
    }

    private String requiredPositionReference(BrokerPositionSnapshotV2Dto position) {
        if (position.brokerPositionReference() == null || position.brokerPositionReference().isBlank()) {
            throw new IllegalStateException("Broker position reference is required for reconciliation");
        }
        return position.brokerPositionReference();
    }

    private void updateOpenPosition(Trade trade, BrokerPositionSnapshotV2Dto position) {
        trade.setSymbol(position.instrument());
        trade.setType(position.signedQuantity().signum() > 0 ? TradeType.BUY : TradeType.SELL);
        trade.setEntryPrice(position.entryPrice());
        trade.setQuantity(position.signedQuantity().abs());
        trade.setTradeStatus(TradeStatus.OPEN);
        if (trade.getBrokerOriginalQuantity() == null) {
            trade.setBrokerOriginalQuantity(position.signedQuantity().abs());
        }
        if (position.stopLoss() != null) {
            trade.setStopLoss(position.stopLoss());
        }
    }

    private List<BrokerDealSnapshotV2Dto> dealsForPosition(
            List<BrokerDealSnapshotV2Dto> deals,
            String positionReference) {
        return deals.stream()
                .filter(deal -> deal.closingPosition())
                .filter(deal -> positionReference.equals(deal.providerPositionReference()))
                .toList();
    }

    private void applyClosingDeals(Trade trade,
                                   List<BrokerDealSnapshotV2Dto> deals,
                                   boolean positionPresent) {
        if (deals.isEmpty()) {
            return;
        }
        Set<String> appliedReferences = closeDealReferences(trade.getBrokerCloseDealReferences());
        Set<String> baselineReferences = closeDealReferences(trade.getBrokerBaselineCloseDealReferences());
        List<BrokerDealSnapshotV2Dto> unappliedDeals = deals.stream()
                .filter(deal -> !appliedReferences.contains(deal.providerTradeReference()))
                .toList();
        List<BrokerDealSnapshotV2Dto> newDeals = unappliedDeals.stream()
                .filter(deal -> !baselineReferences.contains(deal.providerTradeReference()))
                .toList();
        if (!positionPresent && newDeals.isEmpty()) {
            newDeals = unappliedDeals.stream()
                    .filter(deal -> baselineReferences.contains(deal.providerTradeReference()))
                    .toList();
        }
        if (newDeals.isEmpty()) {
            return;
        }

        BigDecimal closeQuantity = newDeals.stream()
                .map(BrokerDealSnapshotV2Dto::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal closePnl = newDeals.stream()
                .map(BrokerDealSnapshotV2Dto::realizedPnl)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal closeNotional = newDeals.stream()
                .map(deal -> deal.price().multiply(deal.quantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal originalQuantity = trade.getBrokerOriginalQuantity();
        BigDecimal accumulatedClosedQuantity = valueOrZero(trade.getBrokerClosedQuantity());
        if (positionPresent && originalQuantity != null
                && accumulatedClosedQuantity.add(closeQuantity).compareTo(originalQuantity) >= 0) {
            return;
        }

        newDeals.forEach(deal -> appliedReferences.add(deal.providerTradeReference()));
        trade.setBrokerCloseDealReferences(String.join(",", appliedReferences));
        trade.setBrokerCloseDealReference(newDeals.getLast().providerTradeReference());
        trade.setBrokerClosedQuantity(valueOrZero(trade.getBrokerClosedQuantity()).add(closeQuantity));
        trade.setBrokerClosedNotional(valueOrZero(trade.getBrokerClosedNotional()).add(closeNotional));
        trade.setRealizedPnl(valueOrZero(trade.getRealizedPnl()).add(closePnl));

        if (originalQuantity == null || trade.getBrokerClosedQuantity().compareTo(originalQuantity) < 0) {
            return;
        }

        trade.setQuantity(originalQuantity);
        trade.setExitPrice(trade.getBrokerClosedNotional()
                .divide(trade.getBrokerClosedQuantity(), 12, RoundingMode.HALF_UP));
        trade.setPnl(trade.getRealizedPnl());
        trade.setClosedAt(newDeals.stream()
                .map(BrokerDealSnapshotV2Dto::closedAt)
                .max(java.util.Comparator.naturalOrder())
                .orElse(null));
        if (trade.getClosedAt() != null) {
            trade.setTradeStatus(TradeStatus.CLOSED);
        }
    }

    private void rememberClosingDealReferences(Trade trade, List<BrokerDealSnapshotV2Dto> deals) {
        if (deals.isEmpty()) {
            return;
        }
        Set<String> references = closeDealReferences(trade.getBrokerBaselineCloseDealReferences());
        deals.forEach(deal -> references.add(deal.providerTradeReference()));
        trade.setBrokerBaselineCloseDealReferences(String.join(",", references));
    }

    private Set<String> closeDealReferences(String value) {
        Set<String> references = new java.util.LinkedHashSet<>();
        if (value != null && !value.isBlank()) {
            for (String reference : value.split(",")) {
                if (!reference.isBlank()) references.add(reference);
            }
        }
        return references;
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private boolean isClosed(Trade trade) {
        return trade.getTradeStatus() == TradeStatus.CLOSED || trade.getClosedAt() != null;
    }
    // TODO : remplacer par un calcul basé sur la valorisation complète des actifs
    // lorsque MarketDataService sera disponible
    private void recalculateAccount(Account account) {

        BigDecimal availableBalance =
                account.getBalances()
                        .stream()
                        .filter(balance ->
                                balance.getAsset()
                                        .equalsIgnoreCase(account.getBaseCurrency())
                        )
                        .map(AccountBalance::getAmount)
                        .findFirst()
                        .orElse(BigDecimal.ZERO);


        BigDecimal unrealizedPnl =
                account.getTrades()
                        .stream()
                        .filter(trade ->
                                trade.getTradeStatus() == TradeStatus.OPEN
                        )
                        .map(trade ->
                                trade.getPnl() != null
                                        ? trade.getPnl()
                                        : BigDecimal.ZERO
                        )
                        .reduce(BigDecimal.ZERO, BigDecimal::add);


        BigDecimal equity =
                availableBalance.add(unrealizedPnl);


        account.setEquity(equity);


        if (equity.compareTo(account.getPeakEquity()) > 0) {
            account.setPeakEquity(equity);
        }



    }



    }
