package com.designforge.api.auth;

public record AuthResponse(String accessToken, String refreshToken, UserResponse user) {}
