package com.hope.trading.trading_core;

import com.hope.trading.trading_core.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class TradingCoreApplication {

	public static void main(String[] args) {
		SpringApplication.run(TradingCoreApplication.class, args);
	}

}
