package com.hope.trading.trading_core.helper;

import com.hope.trading.trading_core.dto.TradeDto;
import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.dto.UserRequest;
import com.hope.trading.trading_core.model.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    public  UserDto toDto(User user) {
        return UserDto.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .build();
    }
    public User toEntity(UserRequest userRequest) {
        User user = new User();
        user.setUsername(userRequest.getUsername());
        return user;
    }
}
