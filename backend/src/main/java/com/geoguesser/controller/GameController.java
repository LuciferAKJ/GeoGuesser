package com.geoguesser.controller;

import com.geoguesser.dto.GuessRequest;
import com.geoguesser.dto.GuessResult;
import com.geoguesser.service.GameService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/game")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @PostMapping("/guess")
    public ResponseEntity<GuessResult> guess(
            @RequestParam Long playerId,
            @Valid @RequestBody GuessRequest req) {

        return ResponseEntity.ok(gameService.processGuess(playerId, req));
    }

    @GetMapping("/history")
    public ResponseEntity<?> history(@RequestParam Long playerId) {
        return ResponseEntity.ok(gameService.getRecentGames(playerId));
    }
}