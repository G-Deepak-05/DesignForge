package com.designforge.api.auth;

public record UserResponse(String id, String email, String displayName, String locale) {
    static UserResponse from(User user) {
        return new UserResponse(user.getId().toString(), user.getEmail(), user.getDisplayName(), user.getLocale());
    }
}
