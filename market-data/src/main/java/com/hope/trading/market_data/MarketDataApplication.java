package com.hope.trading.market_data;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
@ConfigurationPropertiesScan
public class MarketDataApplication {

	public static void main(String[] args) {
		SpringApplication.run(MarketDataApplication.class, args);
	}

}
