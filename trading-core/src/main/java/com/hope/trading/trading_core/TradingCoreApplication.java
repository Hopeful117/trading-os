package com.hope.trading.trading_core;

import com.hope.trading.trading_core.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableFeignClients
@EnableScheduling
@EnableConfigurationProperties(JwtProperties.class)
public class TradingCoreApplication {

	public static void main(String[] args) {
		SpringApplication.run(TradingCoreApplication.class, args);
	}

}
