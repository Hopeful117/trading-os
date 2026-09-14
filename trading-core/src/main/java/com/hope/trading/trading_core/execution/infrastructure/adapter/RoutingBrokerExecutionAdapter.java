package com.hope.trading.trading_core.execution.infrastructure.adapter;

import com.hope.trading.trading_core.execution.application.port.BrokerExecutionPort;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class RoutingBrokerExecutionAdapter implements BrokerExecutionPort {

    private final BrokerExecutionPort liveAdapter;
    private final BrokerExecutionPort paperAdapter;
    private final BrokerAccountRepository brokerAccountRepository;

    public RoutingBrokerExecutionAdapter(BrokerExecutionAdapter liveAdapter,
                                         SimulatedExecutionAdapter paperAdapter,
                                         BrokerAccountRepository brokerAccountRepository) {
        this.liveAdapter = liveAdapter;
        this.paperAdapter = paperAdapter;
        this.brokerAccountRepository = brokerAccountRepository;
    }

    @Override
    public SubmissionResult submit(ExecutionRequest request) {
        Optional<com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount> brokerAccountOpt =
                brokerAccountRepository.findById(request.brokerAccountId());
        if (brokerAccountOpt.isEmpty()) {
            return new Rejected(null, "BROKER_ACCOUNT_NOT_FOUND");
        }
        com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount brokerAccount = brokerAccountOpt.get();

        if (brokerAccount.executionMode() == ExecutionMode.PAPER) {
            return paperAdapter.submit(request);
        } else {
            return liveAdapter.submit(request);
        }
    }

    @Override
    public void cancel(UUID brokerAccountId, String externalOrderId) {
        Optional<com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount> brokerAccountOpt =
                brokerAccountRepository.findById(brokerAccountId);
        if (brokerAccountOpt.isEmpty()) {
            return; // or throw exception
        }
        com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount brokerAccount = brokerAccountOpt.get();

        if (brokerAccount.executionMode() == ExecutionMode.PAPER) {
            paperAdapter.cancel(brokerAccountId, externalOrderId);
        } else {
            liveAdapter.cancel(brokerAccountId, externalOrderId);
        }
    }

    @Override
    public ReconciliationResult reconcile(ReconciliationRequest request) {
        Optional<com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount> brokerAccountOpt =
                brokerAccountRepository.findById(request.brokerAccountId());
        if (brokerAccountOpt.isEmpty()) {
            return new Inconsistent("BROKER_ACCOUNT_NOT_FOUND");
        }
        com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount brokerAccount = brokerAccountOpt.get();

        if (brokerAccount.executionMode() == ExecutionMode.PAPER) {
            return paperAdapter.reconcile(request);
        } else {
            return liveAdapter.reconcile(request);
        }
    }
}