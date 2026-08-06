package com.geoguesser.controller;

import com.geoguesser.config.JwtUtil;
import com.geoguesser.model.Player;
import com.geoguesser.repository.PlayerRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final PlayerRepository playerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(PlayerRepository playerRepository,PasswordEncoder passwordEncoder,JwtUtil jwtUtil) {
        this.playerRepository = playerRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String email = body.get("email");
        String password = body.get("password");

        if (playerRepository.existsByUsername(username))
            return ResponseEntity.badRequest().body("Username taken");

        if (playerRepository.existsByEmail(email))
            return ResponseEntity.badRequest().body("Email already registered");

        Player player = new Player(username, email, passwordEncoder.encode(password));
        playerRepository.save(player);

        return ResponseEntity.ok(Map.of("message", "Registered successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        return playerRepository.findByUsername(username)
                .filter(p -> passwordEncoder.matches(password, p.getPasswordHash()))
                .map(p -> ResponseEntity.ok(Map.of(
                        "token", jwtUtil.generateToken(p.getUsername()),
                        "playerId", p.getId(),
                        "username", p.getUsername()
                )))
                .orElse(ResponseEntity.status(401)
                        .body(Map.of("error", "Invalid credentials")));
    }
}