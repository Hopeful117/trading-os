package com.hope.trading.trading_core.controller;

import com.hope.trading.trading_core.dto.LoginRequest;
import com.hope.trading.trading_core.dto.UserDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/users")
public class internalController {

    private final AuthenticationManager authenticationManager;

    @PostMapping("/authenticate")
    public ResponseEntity<UserDto> authenticate(
            @RequestBody LoginRequest request
    ){

        Authentication authentication =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                request.getUsername(),
                                request.getPassword()
                        )
                );


        UserDetails userDetails =
                (UserDetails) authentication.getPrincipal();


        assert userDetails != null;
        return ResponseEntity.ok(
                UserDto.builder()
                        .username(userDetails.getUsername())
                        .build()
        );
    }
}
