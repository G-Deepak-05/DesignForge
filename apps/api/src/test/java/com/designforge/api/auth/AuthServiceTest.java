package com.designforge.api.auth;

import com.designforge.api.common.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @InjectMocks
    private AuthService authService;

    private static User userWithId(UUID id, String email, String passwordHash) {
        User user = new User(email, passwordHash, "Jane", "en");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    void register_withDuplicateEmail_throws409() {
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () -> authService.register(
                new RegisterRequest("jane@example.com", "password123", "Jane", "en")));

        assertEquals(409, ex.getStatus());
        assertEquals("Email already registered", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_withNewEmail_savesAndReturnsUserResponse() {
        UUID id = UUID.randomUUID();
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User toSave = invocation.getArgument(0);
                    ReflectionTestUtils.setField(toSave, "id", id);
                    return toSave;
                });

        UserResponse response = authService.register(
                new RegisterRequest("jane@example.com", "password123", "Jane", "en"));

        assertEquals(id.toString(), response.id());
        assertEquals("jane@example.com", response.email());
        assertEquals("Jane", response.displayName());
        assertEquals("en", response.locale());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals("hashed", saved.getValue().getPasswordHash());
    }

    @Test
    void register_withNullLocale_defaultsToEn() {
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User toSave = invocation.getArgument(0);
                    ReflectionTestUtils.setField(toSave, "id", UUID.randomUUID());
                    return toSave;
                });

        UserResponse response = authService.register(
                new RegisterRequest("jane@example.com", "password123", "Jane", null));

        assertEquals("en", response.locale());
    }

    @Test
    void login_withUnknownEmail_throws401() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> authService.login(new LoginRequest("nobody@example.com", "password123")));

        assertEquals(401, ex.getStatus());
        assertEquals("Invalid email or password", ex.getMessage());
        verify(refreshTokenStore, never()).store(any(), any());
    }

    @Test
    void login_withWrongPassword_throws401WithSameMessageAsUnknownEmail() {
        User user = userWithId(UUID.randomUUID(), "jane@example.com", "hashed");
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hashed")).thenReturn(false);

        ApiException wrongPassword = assertThrows(ApiException.class,
                () -> authService.login(new LoginRequest("jane@example.com", "wrong-password")));

        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        ApiException unknownEmail = assertThrows(ApiException.class,
                () -> authService.login(new LoginRequest("nobody@example.com", "wrong-password")));

        // No user enumeration: identical status and message for both failure modes.
        assertEquals(unknownEmail.getStatus(), wrongPassword.getStatus());
        assertEquals(unknownEmail.getMessage(), wrongPassword.getMessage());
        verify(refreshTokenStore, never()).store(any(), any());
    }

    @Test
    void login_withValidCredentials_returnsTokensAndStoresRefreshToken() {
        UUID id = UUID.randomUUID();
        User user = userWithId(id, "jane@example.com", "hashed");
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(jwtService.generateAccessToken(user)).thenReturn("access-token-value");

        AuthResponse response = authService.login(new LoginRequest("jane@example.com", "password123"));

        assertEquals("access-token-value", response.accessToken());
        assertEquals(id.toString(), response.user().id());

        ArgumentCaptor<String> refreshToken = ArgumentCaptor.forClass(String.class);
        verify(refreshTokenStore).store(eq(id), refreshToken.capture());
        assertEquals(refreshToken.getValue(), response.refreshToken());
        // The refresh token must be an opaque random value, not a copy of the access token.
        assertNotEquals(response.accessToken(), response.refreshToken());
        assertDoesNotThrow(() -> UUID.fromString(response.refreshToken()));
    }

    @Test
    void me_withMissingUser_throws404() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> authService.me(id));

        assertEquals(404, ex.getStatus());
        assertEquals("User not found", ex.getMessage());
    }

    @Test
    void me_withExistingUser_returnsUserResponse() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(userWithId(id, "jane@example.com", "hashed")));

        UserResponse response = authService.me(id);

        assertEquals(id.toString(), response.id());
        assertEquals("jane@example.com", response.email());
    }

    @Test
    void logout_revokesRefreshTokenForUser() {
        UUID id = UUID.randomUUID();

        authService.logout(id);

        verify(refreshTokenStore).revoke(id);
    }
}
