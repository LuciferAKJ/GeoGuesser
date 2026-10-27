package com.geoguesser.service;

import com.geoguesser.config.JwtUtil;
import com.geoguesser.dto.AuthResponse;
import com.geoguesser.dto.LoginRequest;
import com.geoguesser.dto.MessageResponse;
import com.geoguesser.dto.RegisterRequest;
import com.geoguesser.exception.AccountNotVerifiedException;
import com.geoguesser.model.Player;
import com.geoguesser.model.VerificationToken;
import com.geoguesser.repository.PlayerRepository;
import com.geoguesser.repository.VerificationTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int COOLDOWN_SECONDS = 60;
    private static final int TOKEN_EXPIRY_MINUTES = 30;
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,30}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final PlayerRepository playerRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            PlayerRepository playerRepository,
            VerificationTokenRepository verificationTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil,
            EmailService emailService
    ) {
        this.playerRepository = playerRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.emailService = emailService;
    }

    @Transactional
    public MessageResponse register(RegisterRequest request) {
        String username = request.getUsername() != null ? request.getUsername().trim() : "";
        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        String password = request.getPassword() != null ? request.getPassword() : "";

        validateRegistrationInput(username, email, password);

        if (playerRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("Username is already taken");
        }

        if (playerRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Email is already registered");
        }

        boolean requireVerification = emailService.isVerificationRequired();
        if (requireVerification && !emailService.isConfigured()) {
            log.error("Registration rejected: Email verification is required but email provider is not configured.");
            throw new IllegalStateException("Email service is not configured. Please contact the administrator.");
        }

        Player player = new Player(
                username,
                email,
                passwordEncoder.encode(password),
                !requireVerification
        );
        playerRepository.save(player);

        if (requireVerification) {
            String rawToken = generateSecureToken();
            String tokenHash = hashToken(rawToken);

            VerificationToken token = new VerificationToken(
                    tokenHash,
                    player,
                    LocalDateTime.now().plusMinutes(TOKEN_EXPIRY_MINUTES)
            );
            verificationTokenRepository.save(token);

            emailService.sendVerificationEmail(player.getEmail(), player.getUsername(), rawToken);
            return new MessageResponse("Registration successful! Please check your email inbox to verify your account before logging in.", true);
        }

        return new MessageResponse("Registered successfully", false);
    }

    @Transactional
    public MessageResponse verifyEmail(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Verification token is required");
        }

        String tokenHash = hashToken(rawToken.trim());
        VerificationToken token = verificationTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid verification token"));

        if (token.isUsed()) {
            throw new IllegalStateException("This verification link has already been used");
        }

        if (token.isExpired()) {
            throw new IllegalStateException("This verification link has expired. Please request a new verification email");
        }

        token.setUsedAt(LocalDateTime.now());
        verificationTokenRepository.save(token);

        Player player = token.getPlayer();
        player.setIsVerified(true);
        playerRepository.save(player);

        log.info("Player account {} successfully verified via token", player.getUsername());
        return new MessageResponse("Email verified successfully! You can now log in.");
    }

    @Transactional
    public MessageResponse resendVerification(String emailOrUsername) {
        if (emailOrUsername == null || emailOrUsername.isBlank()) {
            throw new IllegalArgumentException("Email or username is required");
        }

        String identifier = emailOrUsername.trim();
        Player player = playerRepository.findByEmailIgnoreCase(identifier)
                .or(() -> playerRepository.findByUsernameIgnoreCase(identifier))
                .orElse(null);

        // Prevent account enumeration: return consistent friendly response if account not found
        if (player == null) {
            return new MessageResponse("If an unverified account exists with that information, a new verification link has been sent.");
        }

        if (player.isAccountVerified()) {
            return new MessageResponse("This account is already verified. You can log in.");
        }

        // Resend rate-limiting / cooldown check
        var latestTokenOpt = verificationTokenRepository.findTopByPlayerOrderByCreatedAtDesc(player);
        if (latestTokenOpt.isPresent()) {
            VerificationToken latestToken = latestTokenOpt.get();
            long elapsedSeconds = Duration.between(latestToken.getCreatedAt(), LocalDateTime.now()).getSeconds();
            if (elapsedSeconds < COOLDOWN_SECONDS) {
                long remaining = COOLDOWN_SECONDS - elapsedSeconds;
                throw new IllegalStateException("Please wait " + remaining + " seconds before requesting another verification email.");
            }
        }

        // Invalidate previous unused tokens for this player
        List<VerificationToken> existingUnused = verificationTokenRepository.findAllByPlayerAndUsedAtIsNull(player);
        for (VerificationToken t : existingUnused) {
            t.setUsedAt(LocalDateTime.now());
        }
        if (!existingUnused.isEmpty()) {
            verificationTokenRepository.saveAll(existingUnused);
        }

        String rawToken = generateSecureToken();
        String tokenHash = hashToken(rawToken);

        VerificationToken newToken = new VerificationToken(
                tokenHash,
                player,
                LocalDateTime.now().plusMinutes(TOKEN_EXPIRY_MINUTES)
        );
        verificationTokenRepository.save(newToken);

        emailService.sendVerificationEmail(player.getEmail(), player.getUsername(), rawToken);
        return new MessageResponse("A new verification link has been sent to your email. Please check your inbox.");
    }

    public AuthResponse login(LoginRequest request) {
        String username = request.getUsername() != null ? request.getUsername().trim() : "";
        String password = request.getPassword() != null ? request.getPassword() : "";

        Player player = playerRepository.findByUsernameIgnoreCase(username)
                .filter(p -> passwordEncoder.matches(password, p.getPasswordHash()))
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!player.isAccountVerified()) {
            throw new AccountNotVerifiedException(
                    "Your email address is not verified. Please check your inbox or request a new verification link."
            );
        }

        String token = jwtUtil.generateToken(player.getUsername());
        return new AuthResponse(token, player.getId(), player.getUsername(), "Login successful");
    }

    public String generateSecureToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return HexFormat.of().formatHex(randomBytes);
    }

    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private void validateRegistrationInput(String username, String email, String password) {
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException("Username must be between 3 and 30 characters and contain only letters, numbers, underscores, or hyphens");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Please provide a valid email address");
        }
        if (password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
    }
}
