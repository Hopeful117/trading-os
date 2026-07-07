package com.hope.trading.trading_core.service;



import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.exception.EntityNotFoundException;
import com.hope.trading.trading_core.helper.Role;
import com.hope.trading.trading_core.helper.UserMapper;
import com.hope.trading.trading_core.dto.UserRequest;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    @Override
    public UserDto createUser(UserRequest userRequest) {
        if(userRepository.existsByUsername(userRequest.getUsername())) {
            throw new IllegalArgumentException(
                    "Username already exists"
            );
        }


        User user = User.builder()
                .username(userRequest.getUsername())
                .email(userRequest.getEmail())
                .password(
                        passwordEncoder.encode(
                                userRequest.getPassword()
                        )
                )
                .role(Role.ROLE_USER)
                .build();


        return userMapper.toDto(
                userRepository.save(user)
        );
    }



    @Override
    public UserDto getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(userMapper::toDto)
                .orElseThrow(() -> new EntityNotFoundException(
                        "User with username " + username + " not found"
                ));
    }

    @Override
    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(userMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteUser(UUID userId) {
        if(!userRepository.existsById(userId)){
            throw new EntityNotFoundException(
                    "User with ID " + userId + " not found"
            );
        }
        userRepository.deleteById(userId);
    }

    @Override
    public UserDto getUserById(UUID userId) {
        return userRepository.findById(userId)
                .map(userMapper::toDto)
                .orElseThrow(() -> new EntityNotFoundException(
                        "User with ID " + userId + " not found"
                ));
    }
}
