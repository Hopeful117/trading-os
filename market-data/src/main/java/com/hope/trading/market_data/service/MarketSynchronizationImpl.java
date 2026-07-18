package com.hope.trading.market_data.service;


import com.hope.trading.market_data.brokerClient.MarketDataProvider;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.repository.MarketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MarketSynchronizationImpl implements MarketSynchronization {
    private final MarketDataProvider marketDataProvider;
    private final MarketRepository marketRepository;

    @Override
    @Transactional
    public void synchronizeMarkets() {


        List<Market> markets = marketDataProvider.getMarkets();

        List<Market> synchronizedMarkets = markets.stream()
                .map(this::mergeMarket)
                .toList();

        marketRepository.saveAll(synchronizedMarkets);


    }

    private Market mergeMarket(Market market) {

        return marketRepository
                .findByProviderAndSymbol(
                        market.getProvider(),
                        market.getSymbol()
                )
                .map(existing -> update(existing, market))
                .orElse(market);
    }


    private Market update(Market existing, Market incoming) {

        existing.setBaseAsset(incoming.getBaseAsset());
        existing.setQuoteAsset(incoming.getQuoteAsset());

        existing.setMarketConstraints(
                incoming.getMarketConstraints()
        );

        updateMarketState(existing, incoming);

        return existing;
    }
    private void updateMarketState(
            Market existing,
            Market incoming
    ) {

        existing.setMarketState(
                incoming.getMarketState()
        );

    }
}

