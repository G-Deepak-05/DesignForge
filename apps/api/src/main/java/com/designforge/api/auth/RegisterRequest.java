package com.designforge.api.auth;

public record RegisterRequest(String email, String password, String displayName, String locale) {}
