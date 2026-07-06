package com.hope.trading.trading_core.service;



import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.helper.UserMapper;
import com.hope.trading.trading_core.dto.UserRequest;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    @Override
    public UserDto createUser(UserRequest userRequest) {
        User user= userMapper.toEntity(userRequest);
        User savedUser = userRepository.save(user);
        return userMapper.toDto(savedUser);
    }

    @Override
    public UserDto getUser(UUID userId) {
        User user = userRepository.findById(userId).orElse(null);
        return user != null ? userMapper.toDto(user) : null;
    }
}
