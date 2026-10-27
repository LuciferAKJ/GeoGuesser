package com.geoguesser.service;

import com.geoguesser.dto.AuthResponse;
import com.geoguesser.dto.LoginRequest;
import com.geoguesser.dto.MessageResponse;
import com.geoguesser.dto.RegisterRequest;
import com.geoguesser.exception.AccountNotVerifiedException;
import com.geoguesser.model.Player;
import com.geoguesser.model.VerificationToken;
import com.geoguesser.repository.PlayerRepository;
import com.geoguesser.repository.VerificationTokenRepository;
import com.geoguesser.config.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private VerificationTokenRepository verificationTokenRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private JwtUtil jwtUtil;

    private PasswordEncoder passwordEncoder;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(
                playerRepository,
                verificationTokenRepository,
                passwordEncoder,
                jwtUtil,
                emailService
        );
    }

    @Nested
    @DisplayName("Registration Tests")
    class RegistrationTests {

        @Test
        @DisplayName("Rejects blank username, invalid email, or short password")
        void testValidationFailures() {
            RegisterRequest shortUser = new RegisterRequest("ab", "test@example.com", "secret123");
            IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class, () -> authService.register(shortUser));
            assertTrue(e1.getMessage().contains("Username must be between 3 and 30 characters"));

            RegisterRequest badEmail = new RegisterRequest("validuser", "notanemail", "secret123");
            IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class, () -> authService.register(badEmail));
            assertTrue(e2.getMessage().contains("valid email address"));

            RegisterRequest shortPass = new RegisterRequest("validuser", "test@example.com", "123");
            IllegalArgumentException e3 = assertThrows(IllegalArgumentException.class, () -> authService.register(shortPass));
            assertTrue(e3.getMessage().contains("Password must be at least 6 characters"));
        }

        @Test
        @DisplayName("Rejects duplicate username (case-insensitive)")
        void testDuplicateUsernameRejection() {
            when(playerRepository.existsByUsernameIgnoreCase("Alice")).thenReturn(true);

            RegisterRequest req = new RegisterRequest("Alice", "alice@example.com", "secret123");
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.register(req));
            assertEquals("Username is already taken", ex.getMessage());
            verify(playerRepository, never()).save(any());
        }

        @Test
        @DisplayName("Rejects duplicate email (case-insensitive)")
        void testDuplicateEmailRejection() {
            when(playerRepository.existsByUsernameIgnoreCase("Bob")).thenReturn(false);
            when(playerRepository.existsByEmailIgnoreCase("bob@example.com")).thenReturn(true);

            RegisterRequest req = new RegisterRequest("Bob", "bob@example.com", "secret123");
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.register(req));
            assertEquals("Email is already registered", ex.getMessage());
            verify(playerRepository, never()).save(any());
        }

        @Test
        @DisplayName("Fails safely when email verification is required but mail service is unconfigured")
        void testMailServiceUnconfigured() {
            when(playerRepository.existsByUsernameIgnoreCase(anyString())).thenReturn(false);
            when(playerRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
            when(emailService.isVerificationRequired()).thenReturn(true);
            when(emailService.isConfigured()).thenReturn(false);

            RegisterRequest req = new RegisterRequest("charlie", "charlie@example.com", "secret123");
            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> authService.register(req));
            assertTrue(ex.getMessage().contains("Email service is not configured"));
            verify(playerRepository, never()).save(any());
        }

        @Test
        @DisplayName("New account remains unverified until activated, password is BCrypt hashed, and token is SHA-256 hashed")
        void testSuccessfulRegistrationWithVerification() {
            when(playerRepository.existsByUsernameIgnoreCase("david")).thenReturn(false);
            when(playerRepository.existsByEmailIgnoreCase("david@example.com")).thenReturn(false);
            when(emailService.isVerificationRequired()).thenReturn(true);
            when(emailService.isConfigured()).thenReturn(true);

            RegisterRequest req = new RegisterRequest("david", "david@example.com", "plainPassword123");
            MessageResponse response = authService.register(req);

            assertTrue(response.isVerificationRequired());
            assertTrue(response.getMessage().contains("check your email inbox"));

            // Verify player was saved as unverified with hashed password
            ArgumentCaptor<Player> playerCaptor = ArgumentCaptor.forClass(Player.class);
            verify(playerRepository).save(playerCaptor.capture());
            Player savedPlayer = playerCaptor.getValue();
            assertEquals("david", savedPlayer.getUsername());
            assertEquals("david@example.com", savedPlayer.getEmail());
            assertFalse(savedPlayer.isAccountVerified());
            assertNotEquals("plainPassword123", savedPlayer.getPasswordHash());
            assertTrue(passwordEncoder.matches("plainPassword123", savedPlayer.getPasswordHash()));

            // Verify verification token was generated and SHA-256 hashed
            ArgumentCaptor<VerificationToken> tokenCaptor = ArgumentCaptor.forClass(VerificationToken.class);
            verify(verificationTokenRepository).save(tokenCaptor.capture());
            VerificationToken savedToken = tokenCaptor.getValue();
            assertNotNull(savedToken.getTokenHash());
            assertEquals(64, savedToken.getTokenHash().length(), "Token hash must be 64-char SHA-256 hex string");
            assertFalse(savedToken.isUsed());
            assertTrue(savedToken.getExpiryDate().isAfter(LocalDateTime.now()));

            // Verify email was dispatched with the raw token
            ArgumentCaptor<String> rawTokenCaptor = ArgumentCaptor.forClass(String.class);
            verify(emailService).sendVerificationEmail(eq("david@example.com"), eq("david"), rawTokenCaptor.capture());
            String rawToken = rawTokenCaptor.getValue();
            assertNotNull(rawToken);
            assertNotEquals(savedToken.getTokenHash(), rawToken, "Raw token must not equal the stored token hash");
        }

        @Test
        @DisplayName("When verification is disabled (dev mode), account is immediately verified with no email sent")
        void testRegistrationWithVerificationDisabled() {
            when(playerRepository.existsByUsernameIgnoreCase("eve")).thenReturn(false);
            when(playerRepository.existsByEmailIgnoreCase("eve@example.com")).thenReturn(false);
            when(emailService.isVerificationRequired()).thenReturn(false);

            RegisterRequest req = new RegisterRequest("eve", "eve@example.com", "secretPassword");
            MessageResponse response = authService.register(req);

            assertFalse(response.isVerificationRequired());
            ArgumentCaptor<Player> playerCaptor = ArgumentCaptor.forClass(Player.class);
            verify(playerRepository).save(playerCaptor.capture());
            assertTrue(playerCaptor.getValue().isAccountVerified());
            verify(verificationTokenRepository, never()).save(any());
            verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("Login Tests")
    class LoginTests {

        @Test
        @DisplayName("Unverified account is rejected with AccountNotVerifiedException and no JWT is issued")
        void testUnverifiedAccountLoginRejected() {
            Player unverified = new Player("frank", "frank@example.com", passwordEncoder.encode("secret123"), false);
            unverified.setId(10L);
            when(playerRepository.findByUsernameIgnoreCase("frank")).thenReturn(Optional.of(unverified));

            LoginRequest loginReq = new LoginRequest("frank", "secret123");
            assertThrows(AccountNotVerifiedException.class, () -> authService.login(loginReq));
            verify(jwtUtil, never()).generateToken(anyString());
        }

        @Test
        @DisplayName("Verified account logs in successfully and receives JWT")
        void testVerifiedAccountLoginSuccess() {
            Player verified = new Player("grace", "grace@example.com", passwordEncoder.encode("secret123"), true);
            verified.setId(11L);
            when(playerRepository.findByUsernameIgnoreCase("grace")).thenReturn(Optional.of(verified));
            when(jwtUtil.generateToken("grace")).thenReturn("mock-jwt-token");

            LoginRequest loginReq = new LoginRequest("grace", "secret123");
            AuthResponse resp = authService.login(loginReq);

            assertEquals("mock-jwt-token", resp.getToken());
            assertEquals(11L, resp.getPlayerId());
            assertEquals("grace", resp.getUsername());
        }

        @Test
        @DisplayName("Pre-existing account with isVerified=null is treated as verified and logs in")
        void testLegacyNullVerifiedAccountLogsIn() {
            Player legacy = new Player("helen", "helen@example.com", passwordEncoder.encode("legacyPass"));
            legacy.setId(12L);
            legacy.setIsVerified(null); // legacy null
            when(playerRepository.findByUsernameIgnoreCase("helen")).thenReturn(Optional.of(legacy));
            when(jwtUtil.generateToken("helen")).thenReturn("legacy-jwt");

            LoginRequest loginReq = new LoginRequest("helen", "legacyPass");
            AuthResponse resp = authService.login(loginReq);

            assertEquals("legacy-jwt", resp.getToken());
            assertEquals(12L, resp.getPlayerId());
        }

        @Test
        @DisplayName("Wrong password throws IllegalArgumentException without revealing account status")
        void testWrongPasswordRejected() {
            Player player = new Player("ian", "ian@example.com", passwordEncoder.encode("correctPass"), true);
            when(playerRepository.findByUsernameIgnoreCase("ian")).thenReturn(Optional.of(player));

            LoginRequest loginReq = new LoginRequest("ian", "wrongPass");
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.login(loginReq));
            assertEquals("Invalid credentials", ex.getMessage());
            verify(jwtUtil, never()).generateToken(anyString());
        }
    }

    @Nested
    @DisplayName("Email Verification Token Tests")
    class VerificationTokenTests {

        @Test
        @DisplayName("Rejects blank or invalid verification token")
        void testInvalidToken() {
            assertThrows(IllegalArgumentException.class, () -> authService.verifyEmail("   "));

            when(verificationTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.verifyEmail("non-existent-token"));
            assertEquals("Invalid verification token", ex.getMessage());
        }

        @Test
        @DisplayName("Rejects already-used verification token")
        void testAlreadyUsedToken() {
            Player player = new Player("jack", "jack@example.com", "pass", false);
            VerificationToken token = new VerificationToken("hash123", player, LocalDateTime.now().plusMinutes(15));
            token.setUsedAt(LocalDateTime.now().minusMinutes(5));

            when(verificationTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> authService.verifyEmail("rawToken"));
            assertEquals("This verification link has already been used", ex.getMessage());
            assertFalse(player.isAccountVerified());
        }

        @Test
        @DisplayName("Rejects expired verification token")
        void testExpiredToken() {
            Player player = new Player("kate", "kate@example.com", "pass", false);
            VerificationToken token = new VerificationToken("hash456", player, LocalDateTime.now().minusMinutes(1));

            when(verificationTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> authService.verifyEmail("rawToken"));
            assertTrue(ex.getMessage().contains("has expired"));
            assertFalse(player.isAccountVerified());
        }

        @Test
        @DisplayName("Valid token activates account atomically and marks token used")
        void testSuccessfulVerification() {
            Player player = new Player("leo", "leo@example.com", "pass", false);
            VerificationToken token = new VerificationToken("hash789", player, LocalDateTime.now().plusMinutes(20));

            when(verificationTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

            MessageResponse response = authService.verifyEmail("rawLeoToken");

            assertTrue(response.getMessage().contains("Email verified successfully"));
            assertTrue(player.isAccountVerified());
            assertTrue(token.isUsed());
            assertNotNull(token.getUsedAt());

            verify(verificationTokenRepository).save(token);
            verify(playerRepository).save(player);
        }
    }

    @Nested
    @DisplayName("Resend Verification Tests")
    class ResendVerificationTests {

        @Test
        @DisplayName("Non-existent user returns success without enumerating account")
        void testAntiEnumerationOnNonExistentAccount() {
            when(playerRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());
            when(playerRepository.findByUsernameIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

            MessageResponse resp = authService.resendVerification("nobody@example.com");

            assertTrue(resp.getMessage().contains("If an unverified account exists"));
            verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Already verified user returns success without sending email")
        void testAlreadyVerifiedUserResend() {
            Player verified = new Player("mike", "mike@example.com", "pass", true);
            when(playerRepository.findByEmailIgnoreCase("mike")).thenReturn(Optional.empty());
            when(playerRepository.findByUsernameIgnoreCase("mike")).thenReturn(Optional.of(verified));

            MessageResponse resp = authService.resendVerification("mike");

            assertTrue(resp.getMessage().contains("already verified"));
            verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Enforces 60-second cooldown on verification resends")
        void testResendCooldown() {
            Player unverified = new Player("nancy", "nancy@example.com", "pass", false);
            when(playerRepository.findByEmailIgnoreCase("nancy")).thenReturn(Optional.empty());
            when(playerRepository.findByUsernameIgnoreCase("nancy")).thenReturn(Optional.of(unverified));

            VerificationToken recentToken = new VerificationToken("recentHash", unverified, LocalDateTime.now().plusMinutes(29));
            recentToken.setCreatedAt(LocalDateTime.now().minusSeconds(20)); // Created 20s ago (<60s)
            when(verificationTokenRepository.findTopByPlayerOrderByCreatedAtDesc(unverified))
                    .thenReturn(Optional.of(recentToken));

            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> authService.resendVerification("nancy"));
            assertTrue(ex.getMessage().contains("Please wait"));
            verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Resend creates new token, invalidates old tokens, and dispatches email after cooldown expires")
        void testSuccessfulResend() {
            Player unverified = new Player("oliver", "oliver@example.com", "pass", false);
            when(playerRepository.findByEmailIgnoreCase("oliver")).thenReturn(Optional.empty());
            when(playerRepository.findByUsernameIgnoreCase("oliver")).thenReturn(Optional.of(unverified));

            VerificationToken oldToken = new VerificationToken("oldHash", unverified, LocalDateTime.now().plusMinutes(10));
            oldToken.setCreatedAt(LocalDateTime.now().minusSeconds(75)); // Created 75s ago (>60s)
            when(verificationTokenRepository.findTopByPlayerOrderByCreatedAtDesc(unverified))
                    .thenReturn(Optional.of(oldToken));
            when(verificationTokenRepository.findAllByPlayerAndUsedAtIsNull(unverified))
                    .thenReturn(List.of(oldToken));

            MessageResponse resp = authService.resendVerification("oliver");

            assertTrue(resp.getMessage().contains("verification link has been sent"));
            assertTrue(oldToken.isUsed(), "Old unused token must be invalidated");

            ArgumentCaptor<VerificationToken> newTokenCaptor = ArgumentCaptor.forClass(VerificationToken.class);
            verify(verificationTokenRepository).save(newTokenCaptor.capture());
            assertNotNull(newTokenCaptor.getValue().getTokenHash());

            verify(emailService).sendVerificationEmail(eq("oliver@example.com"), eq("oliver"), anyString());
        }
    }
}
