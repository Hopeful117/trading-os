package com.hope.trading.market_data.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "order-book.acquisition")
public class OrderBookAcquisitionProperties {
    private boolean startupEnabled;
    private List<DesiredSubscription> desiredSubscriptions = new ArrayList<>();

    public boolean isStartupEnabled() {
        return startupEnabled;
    }

    public void setStartupEnabled(boolean startupEnabled) {
        this.startupEnabled = startupEnabled;
    }

    public List<DesiredSubscription> getDesiredSubscriptions() {
        return desiredSubscriptions;
    }

    public void setDesiredSubscriptions(List<DesiredSubscription> desiredSubscriptions) {
        this.desiredSubscriptions = desiredSubscriptions == null
                ? new ArrayList<>()
                : new ArrayList<>(desiredSubscriptions);
    }

    public static class DesiredSubscription {
        private String provider;
        private String symbol;
        private String productType;
        private String providerSymbol;
        private String depth;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getSymbol() {
            return symbol;
        }

        public void setSymbol(String symbol) {
            this.symbol = symbol;
        }

        public String getProductType() {
            return productType;
        }

        public void setProductType(String productType) {
            this.productType = productType;
        }

        public String getProviderSymbol() {
            return providerSymbol;
        }

        public void setProviderSymbol(String providerSymbol) {
            this.providerSymbol = providerSymbol;
        }

        public String getDepth() {
            return depth;
        }

        public void setDepth(String depth) {
            this.depth = depth;
        }
    }
}
