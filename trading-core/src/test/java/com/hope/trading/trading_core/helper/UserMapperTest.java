package com.hope.trading.trading_core.helper;

import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.dto.UserRequest;
import com.hope.trading.trading_core.model.User;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * STORY-0020A: user mapping contract — the persisted entity must never leak
 * a password into the exposed DTO, and registration must always create
 * ROLE_USER accounts.
 */
class UserMapperTest {

    private final UserMapper mapper = new UserMapper();

    @Test
    void toDtoExposesIdentityButNeverPassword() {
        User user = User.builder()
                .userId(UUID.randomUUID())
                .username("trader")
                .email("trader@example.com")
                .password("super-secret")
                .role(Role.ROLE_ADMIN)
                .build();

        UserDto dto = mapper.toDto(user);

        assertThat(dto.getUserId()).isEqualTo(user.getUserId());
        assertThat(dto.getUsername()).isEqualTo("trader");
        assertThat(dto.getEmail()).isEqualTo("trader@example.com");
        assertThat(dto.getRole()).isEqualTo(Role.ROLE_ADMIN);
        assertThat(dto.toString()).doesNotContain("super-secret");
    }

    @Test
    void toEntityAlwaysCreatesRoleUserRegardlessOfInput() {
        UserRequest request = new UserRequest();
        request.setUsername("new-trader");
        request.setEmail("new@example.com");
        request.setPassword("password123");

        User entity = mapper.toEntity(request);

        assertThat(entity.getUsername()).isEqualTo("new-trader");
        assertThat(entity.getEmail()).isEqualTo("new@example.com");
        assertThat(entity.getPassword()).isEqualTo("password123");
        assertThat(entity.getRole()).isEqualTo(Role.ROLE_USER);
    }
}
