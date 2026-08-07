package com.geoguesser.controller;

import com.geoguesser.dto.LeaderboardDTO;
import com.geoguesser.dto.PlayerStatsDTO;
import com.geoguesser.service.PlayerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/player")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<List<LeaderboardDTO>> leaderboard() {
        return ResponseEntity.ok(playerService.getTopLeaderboard());
    }

    @GetMapping("/{id}/stats")
    public ResponseEntity<PlayerStatsDTO> stats(@PathVariable Long id) {
        return ResponseEntity.ok(playerService.getPlayerStats(id));
    }
}