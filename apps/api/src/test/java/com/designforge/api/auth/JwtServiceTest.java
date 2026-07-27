package com.designforge.api.auth;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService("test-secret-key-that-is-at-least-32-bytes-long", 15);

    @Test
    void generateAndParse_roundTripsUserId() {
        UUID userId = UUID.randomUUID();

        String token = jwtService.generateAccessTokenForUserId(userId);
        UUID parsed = jwtService.parseUserId(token);

        assertEquals(userId, parsed);
    }
}
