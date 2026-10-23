package com.hope.trading.trading_core.broker.service;

import com.hope.trading.trading_core.broker.apiClient.BrokerApiClient;
import com.hope.trading.trading_core.broker.apiClient.BrokerAccountSnapshotV2Client;
import com.hope.trading.trading_core.broker.dto.BrokerAccountDto;
import com.hope.trading.trading_core.broker.dto.BrokerAccountSnapshotV2Dto;
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
        if (account.getBroker() != null
                && !brokerAccount.providerRegistrationReference().toString().equals(account.getBroker())) {
            throw new IllegalStateException("Account provider metadata mismatch");
        }
        if (brokerAccount.externalAccountId() != null
                && !brokerAccount.externalAccountId().equals(snapshot.externalAccountId())) {
            throw new IllegalStateException("External broker account identity mismatch");
        }

        replaceBalances(account, snapshot.balances());
        replaceOpenPositions(account, snapshot.positions());
        account.setMarketDataTimestamp(snapshot.observedAt());
        recalculateAccount(account);
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

    private void replaceOpenPositions(Account account, java.util.List<BrokerPositionSnapshotV2Dto> positions) {
        account.getTrades().removeIf(trade -> trade.getTradeStatus() == TradeStatus.OPEN);
        positions.forEach(position -> {
            if (position.signedQuantity() == null || position.signedQuantity().signum() == 0) {
                throw new IllegalStateException("Broker position quantity must be non-zero");
            }
            account.addTrade(Trade.builder()
                    .symbol(position.instrument())
                    .type(position.signedQuantity().signum() > 0 ? TradeType.BUY : TradeType.SELL)
                    .entryPrice(position.entryPrice())
                    .quantity(position.signedQuantity().abs())
                    .tradeStatus(TradeStatus.OPEN)
                    .build());
        });
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
