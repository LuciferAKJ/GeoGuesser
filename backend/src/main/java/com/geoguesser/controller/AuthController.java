package com.geoguesser.controller;

import com.geoguesser.config.JwtUtil;
import com.geoguesser.dto.AuthResponse;
import com.geoguesser.dto.LoginRequest;
import com.geoguesser.dto.MessageResponse;
import com.geoguesser.dto.RegisterRequest;
import com.geoguesser.model.Player;
import com.geoguesser.repository.PlayerRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final PlayerRepository playerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(PlayerRepository playerRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.playerRepository = playerRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        // TODO: Replace with custom exception (e.g. UsernameAlreadyExistsException)
        if (playerRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Username taken"));
        }

        // TODO: Replace with custom exception (e.g. EmailAlreadyExistsException)
        if (playerRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Email already registered"));
        }

        Player player = new Player(
                request.getUsername(),
                request.getEmail(),
                passwordEncoder.encode(request.getPassword())
        );
        playerRepository.save(player);

        return ResponseEntity.ok(new MessageResponse("Registered successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        Player player = playerRepository.findByUsername(request.getUsername())
                .filter(p -> passwordEncoder.matches(request.getPassword(), p.getPasswordHash()))
                // TODO: Replace with custom exception (e.g. InvalidCredentialsException)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        String token = jwtUtil.generateToken(player.getUsername());
        return ResponseEntity.ok(new AuthResponse(token, player.getId(), player.getUsername(), "Login successful"));
    }
}