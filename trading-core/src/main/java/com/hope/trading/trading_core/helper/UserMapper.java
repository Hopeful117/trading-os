package com.hope.trading.trading_core.helper;

import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.dto.UserRequest;
import com.hope.trading.trading_core.model.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserDto toDto(User user) {

        return UserDto.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }


    public User toEntity(UserRequest userRequest) {

        return User.builder()
                .username(userRequest.getUsername())
                .email(userRequest.getEmail())
                .password(userRequest.getPassword())
                .role(Role.ROLE_USER)
                .build();
    }
}