package com.designforge.api.auth;

import com.designforge.api.common.ApiException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenStore refreshTokenStore;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenStore refreshTokenStore
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenStore = refreshTokenStore;
    }

    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ApiException(409, "Email already registered");
        }
        User user = new User(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.displayName(),
                request.locale() == null ? "en" : request.locale()
        );
        return UserResponse.from(userRepository.save(user));
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ApiException(401, "Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(401, "Invalid email or password");
        }
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = java.util.UUID.randomUUID().toString();
        refreshTokenStore.store(user.getId(), refreshToken);
        return new AuthResponse(accessToken, refreshToken, UserResponse.from(user));
    }

    public UserResponse me(java.util.UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(404, "User not found"));
        return UserResponse.from(user);
    }

    public void logout(java.util.UUID userId) {
        refreshTokenStore.revoke(userId);
    }
}
