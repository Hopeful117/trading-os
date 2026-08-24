package com.hope.trading.trading_core.service;

import com.hope.trading.trading_core.dto.UserRequest;
import com.hope.trading.trading_core.exception.EntityNotFoundException;
import com.hope.trading.trading_core.helper.Role;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * STORY-0020A-3C: protects the user registration workflow — duplicate
 * usernames rejected, passwords encoded before persistence, default role
 * enforced, and lookups fail explicitly for unknown users.
 */
class UserServiceImplTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(
                userRepository, new com.hope.trading.trading_core.helper.UserMapper(),
                passwordEncoder);
        when(passwordEncoder.encode(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> "encoded-" + inv.getArgument(0));
    }

    private UserRequest request(String username) {
        UserRequest request = new UserRequest();
        request.setUsername(username);
        request.setEmail(username + "@example.com");
        request.setPassword("plain-secret");
        return request;
    }

    @Test
    void registrationEncodesPasswordAndForcesDefaultRole() {
        when(userRepository.existsByUsername("new-trader")).thenReturn(false);

        service.createUser(request("new-trader"));

        verify(userRepository).save(Mockito.argThat(user ->
                "ROLE_USER".equals(user.getRole().name())
                        && "encoded-plain-secret".equals(user.getPassword())));
    }

    @Test
    void duplicateUsernameIsRejectedWithoutSaving() {
        when(userRepository.existsByUsername("existing")).thenReturn(true);

        assertThatThrownBy(() -> service.createUser(request("existing")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void lookupByUnknownUsernameFailsExplicitly() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUserByUsername("ghost"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    void knownUserIsReturnedById() {
        UUID id = UUID.randomUUID();
        User user = User.builder().userId(id).username("known").build();
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        assertThat(service.getUserById(id)).isSameAs(user);
    }

    @Test
    void deleteOfUnknownUserFailsExplicitly() {
        UUID id = UUID.randomUUID();
        when(userRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> service.deleteUser(id))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining(String.valueOf(id));
    }

    @Test
    void allUsersAreListed() {
        when(userRepository.findAll()).thenReturn(List.of());
        assertThat(service.getAllUsers()).isEmpty();

        List<User> two = List.of(new User(), new User());
        Mockito.reset(userRepository);
        when(userRepository.findAll()).thenReturn(two);

        assertThat(service.getAllUsers()).containsExactlyElementsOf(two);
    }
}
