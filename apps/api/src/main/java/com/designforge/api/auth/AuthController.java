package com.designforge.api.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(java.security.Principal principal) {
        java.util.UUID userId = java.util.UUID.fromString(principal.getName());
        return ResponseEntity.ok(authService.me(userId));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(java.security.Principal principal) {
        java.util.UUID userId = java.util.UUID.fromString(principal.getName());
        authService.logout(userId);
        return ResponseEntity.noContent().build();
    }
}
