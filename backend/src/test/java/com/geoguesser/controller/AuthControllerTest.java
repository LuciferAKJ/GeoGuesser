package com.geoguesser.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoguesser.config.GlobalExceptionHandler;
import com.geoguesser.dto.*;
import com.geoguesser.exception.AccountNotVerifiedException;
import com.geoguesser.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        AuthController controller = new AuthController(authService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("POST /api/auth/register returns 201 Created with message response")
    void testRegisterEndpoint() throws Exception {
        RegisterRequest req = new RegisterRequest("testuser", "test@example.com", "secret123");
        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(new MessageResponse("Registration successful!", true));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Registration successful!")))
                .andExpect(jsonPath("$.verificationRequired", is(true)));

        verify(authService).register(any(RegisterRequest.class));
    }

    @Test
    @DisplayName("POST /api/auth/login returns 200 OK with AuthResponse")
    void testLoginEndpointSuccess() throws Exception {
        LoginRequest req = new LoginRequest("testuser", "secret123");
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new AuthResponse("jwt.token.val", 5L, "testuser", "Login successful"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", is("jwt.token.val")))
                .andExpect(jsonPath("$.playerId", is(5)))
                .andExpect(jsonPath("$.username", is("testuser")));

        verify(authService).login(any(LoginRequest.class));
    }

    @Test
    @DisplayName("POST /api/auth/login with unverified account returns 403 Forbidden with ACCOUNT_NOT_VERIFIED code")
    void testLoginEndpointUnverifiedAccount() throws Exception {
        LoginRequest req = new LoginRequest("unverifiedUser", "secret123");
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new AccountNotVerifiedException("Account is not verified. Please check your email."));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ACCOUNT_NOT_VERIFIED")))
                .andExpect(jsonPath("$.error", is("Account is not verified. Please check your email.")));
    }

    @Test
    @DisplayName("POST /api/auth/verify returns 200 OK with verified status")
    void testVerifyEndpointSuccess() throws Exception {
        VerifyTokenRequest req = new VerifyTokenRequest("validToken123");
        when(authService.verifyEmail("validToken123"))
                .thenReturn(new MessageResponse("Email verified successfully! You can now log in."));

        mockMvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Email verified successfully! You can now log in.")));

        verify(authService).verifyEmail("validToken123");
    }

    @Test
    @DisplayName("POST /api/auth/resend-verification returns 200 OK with message")
    void testResendVerificationEndpoint() throws Exception {
        ResendVerificationRequest req = new ResendVerificationRequest("user@example.com");
        when(authService.resendVerification("user@example.com"))
                .thenReturn(new MessageResponse("If an account exists, a verification link has been sent."));

        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("If an account exists, a verification link has been sent.")));

        verify(authService).resendVerification("user@example.com");
    }
}
