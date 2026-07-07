package com.hope.trading.trading_core.controller;

import com.hope.trading.trading_core.config.CustomUserDetailsService;
import com.hope.trading.trading_core.config.JwtService;
import com.hope.trading.trading_core.dto.*;
import com.hope.trading.trading_core.helper.UserMapper;
import com.hope.trading.trading_core.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller for managing user-related operations.
 * Provides endpoints for creating and retrieving users.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;


    /**
     * Create a user
     * @param userRequest contains the desired user attributes
     * @return user as a DTO
     */
    @PostMapping("/register")
    public ResponseEntity<UserDto> createUser(@RequestBody @Valid UserRequest userRequest) {
        UserDto userDto = userService.createUser(userRequest);
        return ResponseEntity.ok(userDto);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthenticationResponse> login (@RequestBody @Valid LoginRequest loginRequest){
        Authentication authentication =authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );
        UserDetails userDetails= (UserDetails) authentication.getPrincipal();
        return ResponseEntity.ok(AuthenticationResponse.builder().token(jwtService.generateToken(userDetails)).build());

    }

    /**
     * Fetch a user
     * @param id id of the desired user
     * @return user as a DTO
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUser(@PathVariable UUID id) {
        UserDto userDto = userService.getUserById(id);
        return ResponseEntity.ok(userDto);
    }
}
