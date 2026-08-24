package com.hope.trading.trading_core.config;

import com.hope.trading.trading_core.helper.Role;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomUserDetailsServiceTest {

    private UserRepository userRepository;
    private CustomUserDetailsService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        service = new CustomUserDetailsService(userRepository);
    }

    @Test
    void loadUserByUsernameReturnsUserDetailsWithRole() {
        User user = User.builder()
                .userId(UUID.randomUUID())
                .username("trader1")
                .password("hashed-pw")
                .email("trader1@example.com")
                .role(Role.ROLE_USER)
                .build();
        when(userRepository.findByUsername("trader1")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("trader1");

        assertThat(details.getUsername()).isEqualTo("trader1");
        assertThat(details.getPassword()).isEqualTo("hashed-pw");
        assertThat(details.getAuthorities()).hasSize(1);
        assertThat(details.getAuthorities().iterator().next().getAuthority()).isEqualTo("ROLE_USER");
    }

    @Test
    void loadUserByUsernameStripsRolePrefix() {
        User user = User.builder()
                .userId(UUID.randomUUID())
                .username("admin1")
                .password("admin-pw")
                .email("admin@example.com")
                .role(Role.ROLE_ADMIN)
                .build();
        when(userRepository.findByUsername("admin1")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("admin1");

        assertThat(details.getAuthorities().iterator().next().getAuthority()).isEqualTo("ROLE_ADMIN");
    }

    @Test
    void loadUserByUsernameThrowsWhenNotFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("ghost");
    }
}
