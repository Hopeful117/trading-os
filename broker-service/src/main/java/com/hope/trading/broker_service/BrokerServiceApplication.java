package com.hope.trading.broker_service;

import com.hope.trading.broker_service.config.KrakenProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BrokerServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(BrokerServiceApplication.class, args);
	}

}
