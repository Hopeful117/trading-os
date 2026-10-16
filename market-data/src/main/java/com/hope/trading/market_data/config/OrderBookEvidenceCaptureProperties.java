package com.hope.trading.market_data.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "order-book.evidence.capture")
public class OrderBookEvidenceCaptureProperties {
    private boolean enabled;
    private String providerSymbol = "PF_XBTUSD";
    private int depth = 25;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProviderSymbol() {
        return providerSymbol;
    }

    public void setProviderSymbol(String providerSymbol) {
        this.providerSymbol = providerSymbol;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }
}
