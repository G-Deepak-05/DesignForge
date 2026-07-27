package com.designforge.api.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        // max = 72 documents BCrypt's silent truncation limit rather than leaving it implicit.
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 255) String displayName,
        @Size(max = 10) String locale
) {}
