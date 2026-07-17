package com.hope.trading.trading_core.apiClient;

import com.hope.trading.trading_core.dto.BrokerAccountDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(
        name = "broker-service"
)
public interface BrokerFeignClient extends BrokerApiClient{
    @Override
    @GetMapping("/api/v1/broker/account")
    BrokerAccountDto getAccount();

}
