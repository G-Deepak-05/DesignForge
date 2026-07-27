package com.designforge.api.auth;

import com.designforge.api.common.ApiException;
import com.designforge.api.config.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Test
    void register_withValidBody_returns201() throws Exception {
        RegisterRequest request = new RegisterRequest("jane@example.com", "password123", "Jane", "en");
        UserResponse response = new UserResponse("11111111-1111-1111-1111-111111111111", "jane@example.com", "Jane", "en");
        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("jane@example.com"));
    }

    @Test
    void register_withDuplicateEmail_returns409() throws Exception {
        RegisterRequest request = new RegisterRequest("jane@example.com", "password123", "Jane", "en");
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new ApiException(409, "Email already registered"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already registered"));
    }

    @Test
    void login_withValidCredentials_returns200WithTokens() throws Exception {
        LoginRequest request = new LoginRequest("jane@example.com", "password123");
        AuthResponse response = new AuthResponse(
                "access-token-value",
                "refresh-token-value",
                new UserResponse("11111111-1111-1111-1111-111111111111", "jane@example.com", "Jane", "en")
        );
        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token-value"));
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        LoginRequest request = new LoginRequest("jane@example.com", "wrong-password");
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new ApiException(401, "Invalid email or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
