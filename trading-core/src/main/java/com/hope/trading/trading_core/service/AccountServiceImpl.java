package com.hope.trading.trading_core.service;

import com.hope.trading.trading_core.dto.AccountDto;
import com.hope.trading.trading_core.exception.EntityNotFoundException;
import com.hope.trading.trading_core.helper.AccountMapper;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.AccountBalance;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.helper.TradeStatus;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.repository.UserRepository;
import com.hope.trading.trading_core.risk.infrastructure.persistence.RiskPersistence;
import com.hope.trading.trading_core.tradeplanning.application.TradePlanningProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class AccountServiceImpl implements AccountService {


    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final UserRepository userRepository;
    private final RiskPersistence riskPersistence;
    private final TradePlanningProfileRepository tradePlanningProfiles;
    private final TradingCalculatorService tradingCalculatorService;
    private final BrokerAccountRepository brokerAccounts;

    public AccountServiceImpl(AccountRepository accountRepository, AccountMapper accountMapper,
                              UserRepository userRepository, RiskPersistence riskPersistence,
                              TradePlanningProfileRepository tradePlanningProfiles) {
        this(accountRepository, accountMapper, userRepository, riskPersistence, tradePlanningProfiles,
                new TradingCalculatorServiceImpl(), null);
    }

    public AccountServiceImpl(AccountRepository accountRepository, AccountMapper accountMapper,
                              UserRepository userRepository, RiskPersistence riskPersistence,
                              TradePlanningProfileRepository tradePlanningProfiles,
                              TradingCalculatorService tradingCalculatorService) {
        this(accountRepository, accountMapper, userRepository, riskPersistence, tradePlanningProfiles,
                tradingCalculatorService, null);
    }

    @Autowired
    public AccountServiceImpl(AccountRepository accountRepository, AccountMapper accountMapper,
                              UserRepository userRepository, RiskPersistence riskPersistence,
                              TradePlanningProfileRepository tradePlanningProfiles,
                              TradingCalculatorService tradingCalculatorService,
                              BrokerAccountRepository brokerAccounts) {
        this.accountRepository = accountRepository;
        this.accountMapper = accountMapper;
        this.userRepository = userRepository;
        this.riskPersistence = riskPersistence;
        this.tradePlanningProfiles = tradePlanningProfiles;
        this.tradingCalculatorService = tradingCalculatorService;
        this.brokerAccounts = brokerAccounts;
    }


    @Override
    public Account getAccountById(UUID accountId,String username) {
       return accountRepository.findByAccountIdAndUser_Username(accountId,username).orElseThrow(()->new EntityNotFoundException("account not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public AccountDto getAccountDtoById(UUID accountId, String username) {
        return toDto(getAccountById(accountId, username));
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getTotalBalance(UUID accountId,String username) {

        return getAccountById(accountId,username)
                .getBalances()
                .stream()
                .map(AccountBalance::getAmount)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add
                );
    }


    @Override
    @Transactional(readOnly = true)
    public BigDecimal getEquity(UUID accountId,String username) {

        return getAccountById(accountId,username)
                .getEquity();
    }


    @Override
    public void updateEquity(UUID accountId, BigDecimal pnl,String username) {

        Account account = getAccountById(accountId,username);


        boolean cash = isPaperCash(account);
        if (cash) {
            if (account.getRealizedBalance() == null) {
                account.setRealizedBalance(account.getEquity());
            }
            account.setRealizedBalance(account.getRealizedBalance().add(pnl));
            BigDecimal unrealized = account.getTrades().stream()
                    .filter(trade -> trade.getTradeStatus() == TradeStatus.OPEN)
                    .map(trade -> tradingCalculatorService.calculatePnL(trade.getType(), trade.getEntryPrice(),
                            trade.getCurrentPrice() == null ? trade.getEntryPrice() : trade.getCurrentPrice(),
                            trade.getQuantity()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            account.setEquity(account.getRealizedBalance().add(unrealized));
            account.setValuationStatus(account.getTrades().stream()
                    .anyMatch(trade -> trade.getTradeStatus() == TradeStatus.OPEN)
                    ? com.hope.trading.trading_core.model.AccountValuationStatus.STALE
                    : com.hope.trading.trading_core.model.AccountValuationStatus.FRESH);
        } else {
            account.setEquity(account.getEquity().add(pnl));
        }
        BigDecimal newEquity = account.getEquity();


        if ((!cash || account.getValuationStatus() == com.hope.trading.trading_core.model.AccountValuationStatus.FRESH)
                && newEquity.compareTo(account.getPeakEquity()) > 0) {

            account.setPeakEquity(newEquity);

        }


        accountRepository.save(account);
    }

    private boolean isPaperCash(Account account) {
        return account.getSettlementModel() == com.hope.trading.trading_core.shared.domain.model.SettlementModel.CASH
                && brokerAccounts != null
                && account.getBrokerAccountId() != null
                && brokerAccounts.findById(account.getBrokerAccountId())
                .map(value -> value.executionMode() == ExecutionMode.PAPER)
                .orElse(false);
    }


    @Override
    @Transactional(readOnly = true)
    public BigDecimal getCurrentDrawdown(UUID accountId,String username) {

        Account account = getAccountById(accountId,username);


        return account.getPeakEquity()
                .subtract(account.getEquity());
    }















    @Override
    @Transactional(readOnly = true)
    public List<AccountDto> getAllUserAccounts(String username) {
        log.debug("Retrieving account for authenticated user");
        User user = userRepository.findByUsername(username).orElseThrow(()->new EntityNotFoundException("User not found"));

        return accountRepository.findAllByUser_UserId(user.getUserId())
                .stream()
                .map(this::toDto)
                .toList();
    }

    private AccountDto toDto(Account account) {
        AccountDto dto = accountMapper.toDto(account);
        riskPersistence.assignedProfile(account.getAccountId()).ifPresent(profile -> {
            dto.setRiskProfileId(profile.id());
            dto.setRiskProfileSemanticVersion(profile.semanticVersion());
        });
        tradePlanningProfiles.findAssigned(account.getAccountId()).ifPresent(profile -> {
            dto.setTradePlanningProfileId(profile.id());
            dto.setTradePlanningProfileVersion(profile.version());
        });
        return dto;
    }

    @Override
    public BigDecimal getAvailableBalance(UUID accountId, String asset,String username) {
        Account account = getAccountById(accountId,username);

        return account.getBalances()
                .stream()
                .filter(balance ->
                        balance.getAsset().equalsIgnoreCase(asset)
                )
                .map(AccountBalance::getAmount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }



}
