package com.hope.trading.broker_service.kraken.config;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "historical-fee-schedule.capture")
public class HistoricalFeeScheduleCaptureProperties {
    private boolean enabled;
    private List<UUID> brokerAccountIds = new ArrayList<>();
    private String instrument = "PF_XBTUSD";
    private String productType = "LINEAR_PERPETUAL";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public List<UUID> getBrokerAccountIds() { return List.copyOf(brokerAccountIds); }
    public void setBrokerAccountIds(List<UUID> value) {
        brokerAccountIds = value == null ? new ArrayList<>() : new ArrayList<>(value);
    }
    public String getInstrument() { return instrument; }
    public void setInstrument(String value) { instrument = value == null ? "" : value.trim(); }
    public String getProductType() { return productType; }
    public void setProductType(String value) { productType = value == null ? "" : value.trim(); }
}
