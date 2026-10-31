package com.hope.trading.market_data.service;


import com.hope.trading.market_data.brokerclient.MarketDataProvider;
import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.repository.MarketRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MarketSynchronizationImpl implements MarketSynchronization {
    private final Map<MarketProvider, MarketDataProvider> providers;
    private final MarketRepository marketRepository;

    public MarketSynchronizationImpl(MarketDataProvider provider,
                                     MarketRepository marketRepository) {
        this(List.of(provider), marketRepository);
    }

    @Autowired
    public MarketSynchronizationImpl(List<MarketDataProvider> providers,
                                     MarketRepository marketRepository) {
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
                provider -> provider.getName() == null
                        ? MarketProvider.KRAKEN
                        : provider.getName(),
                Function.identity()
        ));
        this.marketRepository = marketRepository;
    }

    @Override
    @Transactional
    public void synchronizeMarkets() {


        List<Market> synchronizedMarkets = providers.values().stream()
                .flatMap(provider -> provider.getMarkets().stream())
                .map(this::mergeMarket)
                .toList();

        marketRepository.saveAll(synchronizedMarkets);


    }

    private Market mergeMarket(Market market) {

        return marketRepository
                .findByProviderAndSymbolAndProductType(
                        market.getProvider(),
                        market.getSymbol(),
                        market.getProductType()
                )
                .map(existing -> update(existing, market))
                .orElse(market);
    }


    private Market update(Market existing, Market incoming) {

        existing.setBaseAsset(incoming.getBaseAsset());
        existing.setQuoteAsset(incoming.getQuoteAsset());
        existing.setProviderSymbol(incoming.getProviderSymbol());
        existing.setQuantityUnit(incoming.getQuantityUnit());
        existing.setContractMultiplier(incoming.getContractMultiplier());
        existing.setContractSourceId(incoming.getContractSourceId());
        existing.setContractSourceVersion(incoming.getContractSourceVersion());
        existing.setContractObservedAt(incoming.getContractObservedAt());

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
