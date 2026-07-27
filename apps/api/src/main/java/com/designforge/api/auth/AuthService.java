package com.designforge.api.auth;

import com.designforge.api.common.ApiException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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
}
