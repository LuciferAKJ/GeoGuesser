package com.geoguesser.controller;

import com.geoguesser.dto.GuessRequest;
import com.geoguesser.dto.GuessResult;
import com.geoguesser.service.GameService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/game")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @PostMapping("/guess")
    public ResponseEntity<GuessResult> guess(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody GuessRequest req) {

        return ResponseEntity.ok(
                gameService.processGuess(user.getUsername(), req)
        );
    }

    @GetMapping("/history")
    public ResponseEntity<?> history(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(gameService.getRecentGames(user.getUsername()));
    }
}