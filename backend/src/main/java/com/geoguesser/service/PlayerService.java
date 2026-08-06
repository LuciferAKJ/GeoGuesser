package com.geoguesser.service;

import com.geoguesser.model.Player;
import com.geoguesser.repository.PlayerRepository;
import com.geoguesser.config.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public PlayerService(PlayerRepository playerRepository,
                         PasswordEncoder passwordEncoder,
                         JwtUtil jwtUtil) {
        this.playerRepository = playerRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public Player register(String username, String email, String password) {
        if (playerRepository.existsByUsername(username))
            throw new RuntimeException("Username already taken");

        if (playerRepository.existsByEmail(email))
            throw new RuntimeException("Email already registered");

        Player p = new Player(username, email, passwordEncoder.encode(password));
        return playerRepository.save(p);
    }

    public String login(String username, String password) {
        Player p = playerRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (!passwordEncoder.matches(password, p.getPasswordHash()))
            throw new RuntimeException("Invalid credentials");

        return jwtUtil.generateToken(p.getUsername());
    }

    public List<Map<String, Object>> getLeaderboard() {
        List<Player> top = playerRepository.findTop10ByOrderByTotalScoreDesc();
        List<Map<String, Object>> result = new ArrayList<>();

        for (int i = 0; i < top.size(); i++) {
            Player p = top.get(i);

            result.add(Map.of(
                    "rank", i + 1,
                    "username", p.getUsername(),
                    "totalScore", p.getTotalScore(),
                    "gamesPlayed", p.getGamesPlayed(),
                    "bestScore", p.getBestScore()
            ));
        }

        return result;
    }

    public Map<String, Object> getStats(Long playerId) {
        Player p = playerRepository.findById(playerId)
                .orElseThrow(() -> new RuntimeException("Player not found"));

        return Map.of(
                "username", p.getUsername(),
                "totalScore", p.getTotalScore(),
                "gamesPlayed", p.getGamesPlayed(),
                "bestScore", p.getBestScore()
        );
    }
}