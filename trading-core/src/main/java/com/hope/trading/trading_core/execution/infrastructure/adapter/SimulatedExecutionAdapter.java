package com.hope.trading.trading_core.execution.infrastructure.adapter;

import com.hope.trading.trading_core.execution.application.port.BrokerExecutionPort;
import com.hope.trading.trading_core.execution.domain.model.ExecutionParameters;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import com.hope.trading.trading_core.market_data.dto.MarketPriceSnapshotDto;
import com.hope.trading.trading_core.market_data.dto.MarketPriceSnapshotRequest;
import com.hope.trading.trading_core.market_data.dto.MarketResponse;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.market_data.apiClient.MarketDataClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class SimulatedExecutionAdapter implements BrokerExecutionPort {

    private final BrokerAccountRepository brokerAccountRepository;
    private final MarketDataClient marketDataClient;

    public SimulatedExecutionAdapter(BrokerAccountRepository brokerAccountRepository,
                                     MarketDataClient marketDataClient) {
        this.brokerAccountRepository = brokerAccountRepository;
        this.marketDataClient = marketDataClient;
    }

    @Override
    public SubmissionResult submit(ExecutionRequest request) {
        // Validate that this is a PAPER account
        Optional<com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount> brokerAccountOpt =
                brokerAccountRepository.findById(request.brokerAccountId());
        if (brokerAccountOpt.isEmpty()) {
            return new Rejected(null, "BROKER_ACCOUNT_NOT_FOUND");
        }
        com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount brokerAccount = brokerAccountOpt.get();
        if (brokerAccount.executionMode() != ExecutionMode.PAPER) {
            return new Rejected(null, "NOT_A_PAPER_ACCOUNT");
        }

        // Find market by symbol
        List<MarketResponse> allMarkets = marketDataClient.findAll();
        Optional<MarketResponse> marketOpt = allMarkets.stream()
                .filter(m -> m.getSymbol().equals(request.parameters().instrument()))
                .findFirst();

        if (marketOpt.isEmpty()) {
            return new Rejected(null, "MARKET_NOT_FOUND");
        }
        UUID marketId = marketOpt.get().getMarketId();

        // Get current market price for the instrument
        MarketPriceSnapshotRequest priceRequest = new MarketPriceSnapshotRequest(List.of(marketId));
        List<MarketPriceSnapshotDto> priceSnapshots = marketDataClient.findPriceSnapshots(priceRequest);
        if (priceSnapshots.isEmpty()) {
            return new Rejected(null, "MARKET_DATA_UNAVAILABLE");
        }
        MarketPriceSnapshotDto snapshot = priceSnapshots.get(0);

        // Determine fill price: BUY at ask, SELL at bid
        BigDecimal fillPrice;
        if (request.parameters().side() == ExecutionParameters.Side.BUY) {
            fillPrice = snapshot.ask();
        } else {
            fillPrice = snapshot.bid();
        }

        if (fillPrice == null || fillPrice.signum() <= 0) {
            return new Rejected(null, "INVALID_MARKET_PRICE");
        }

        // Check if limit price is satisfied (for LIMIT orders)
        if (request.parameters().orderType() == ExecutionParameters.OrderType.LIMIT) {
            BigDecimal limitPrice = request.parameters().limitPrice();
            if (limitPrice != null) {
                boolean limitSatisfied = request.parameters().side() == ExecutionParameters.Side.BUY
                        ? fillPrice.compareTo(limitPrice) <= 0
                        : fillPrice.compareTo(limitPrice) >= 0;
                if (!limitSatisfied) {
                    return new Rejected(null, "LIMIT_PRICE_NOT_REACHED");
                }
            }
        }

        // Generate simulated external order ID and correlation ID
        String externalOrderId = "SIM-" + java.util.UUID.randomUUID().toString();
        String correlationId = "corr-" + java.util.UUID.randomUUID().toString();

        return new Acknowledged(externalOrderId, correlationId, fillPrice);
    }

    @Override
    public void cancel(UUID brokerAccountId, String externalOrderId) {
        // For PAPER, cancellation is always successful (no-op for simulated orders)
        // Real implementation would track pending orders
    }

    @Override
    public ReconciliationResult reconcile(ReconciliationRequest request) {
        // PAPER state is local; external broker reconciliation cannot prove it.
        return new Inconsistent("PAPER_RECONCILIATION_NOT_SUPPORTED");
    }
}
