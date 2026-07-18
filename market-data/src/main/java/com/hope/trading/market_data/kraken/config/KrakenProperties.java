package com.hope.trading.market_data.kraken.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties(prefix="kraken")
@Getter
@Setter
public class KrakenProperties {

    private String baseUrl;
    private String websocket;

}
