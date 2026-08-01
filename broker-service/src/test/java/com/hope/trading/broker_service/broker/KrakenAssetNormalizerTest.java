package com.hope.trading.broker_service.broker;

import static org.assertj.core.api.Assertions.*;

import com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.BrokerProtocolException;
import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.mapper.KrakenAssetNormalizer;
import org.junit.jupiter.api.Test;

class KrakenAssetNormalizerTest {
    @Test
    void normalizesKnownLegacyAssetsAndPairs() {
        assertThat(KrakenAssetNormalizer.asset("ZUSD")).isEqualTo("USD");
        assertThat(KrakenAssetNormalizer.asset("XXBT")).isEqualTo("BTC");
        assertThat(KrakenAssetNormalizer.pair("XXBTZUSD").instrument()).isEqualTo("BTC/USD");
        assertThat(KrakenAssetNormalizer.pair("ETH/USDT").instrument()).isEqualTo("ETH/USDT");
    }

    @Test
    void rejectsUnknownAssetsAndPairsRatherThanGuessing() {
        assertThatThrownBy(()->KrakenAssetNormalizer.asset("ZUNKNOWN"))
                .isInstanceOf(BrokerProtocolException.class);
        assertThatThrownBy(()->KrakenAssetNormalizer.pair("UNKNOWNZUSD"))
                .isInstanceOf(BrokerProtocolException.class);
    }
}
