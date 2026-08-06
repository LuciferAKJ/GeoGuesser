package com.geoguesser.controller;

import com.geoguesser.model.Player;
import com.geoguesser.repository.PlayerRepository;
import com.geoguesser.service.GameService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/player")
public class PlayerController {

    private final PlayerRepository playerRepository;
    private final GameService gameService;

    public PlayerController(PlayerRepository playerRepository,
                            GameService gameService) {
        this.playerRepository = playerRepository;
        this.gameService = gameService;
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<List<Player>> leaderboard() {
        return ResponseEntity.ok(
                playerRepository.findTop10ByOrderByTotalScoreDesc()
        );
    }

    @GetMapping("/{id}/stats")
    public ResponseEntity<?> stats(@PathVariable Long id) {
        return playerRepository.findById(id)
                .map(p -> ResponseEntity.ok(Map.of(
                        "username", p.getUsername(),
                        "totalScore", p.getTotalScore(),
                        "gamesPlayed", p.getGamesPlayed(),
                        "bestScore", p.getBestScore(),
                        "averageScore", gameService.getAverageScore(id).orElse(0.0)
                )))
                .orElse(ResponseEntity.notFound().build());
    }
}