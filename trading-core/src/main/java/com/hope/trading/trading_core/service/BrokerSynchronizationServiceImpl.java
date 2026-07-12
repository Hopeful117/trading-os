package com.hope.trading.trading_core.service;

import com.hope.trading.trading_core.apiClient.BrokerApiClient;
import com.hope.trading.trading_core.dto.BrokerAccountDto;
import com.hope.trading.trading_core.dto.TradeCalculation;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class BrokerSynchronizationServiceImpl implements BrokerSynchronizationService{
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TradeRepository tradeRepository;
    private final TradingCalculatorService tradingCalculatorService;
    private final BrokerApiClient brokerApiClient;
    private final AccountMapper accountMapper;

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
    }

    private Account synchronizeAccount(User user,BrokerAccountDto snapshot){

       Account account= accountRepository.findByUser_UserIdAndBroker(user.getUserId(),snapshot.getBroker()).orElse(null);
        if (account == null) {

            account = accountMapper.toEntity(snapshot);

            user.addAccount(account);

        } else {

            accountMapper.updateEntity(account,snapshot);

        }

        return accountRepository.save(account);



    }
    private void synchronizeBalances(
            Account account,
            BrokerAccountDto snapshot
    ){
        account.getBalances().clear();

        snapshot.getBalances()
                .getBalances()
                .forEach((asset, amount) -> {

                    AccountBalance balance =
                            AccountBalance.builder()
                                    .asset(asset)
                                    .amount(amount)
                                    .account(account)
                                    .build();

                    account.getBalances().add(balance);
                });
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
                            .entryPrice(position.getEntryValue())
                            .quantity(position.getQuantity())
                            .tradeStatus(TradeStatus.OPEN)
                            .account(account)
                            .build();

                    account.getTrades().add(trade);
                });

        accountRepository.save(account);

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


        accountRepository.save(account);
    }



    }

