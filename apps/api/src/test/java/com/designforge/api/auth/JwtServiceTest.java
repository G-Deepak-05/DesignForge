package com.designforge.api.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = "jwt.secret=test-secret-key-that-is-at-least-32-bytes-long")
class JwtServiceTest {

    @Autowired
    private JwtService jwtService;

    @Test
    void generateAndParse_roundTripsUserId() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateAccessTokenForUserId(userId);

        UUID parsed = jwtService.parseUserId(token);

        assertEquals(userId, parsed);
    }
}
