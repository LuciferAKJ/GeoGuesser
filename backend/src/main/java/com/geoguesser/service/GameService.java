package com.geoguesser.service;

import com.geoguesser.dto.GuessRequest;
import com.geoguesser.dto.GuessResult;
import com.geoguesser.model.Game;
import com.geoguesser.model.Location;
import com.geoguesser.model.Player;
import com.geoguesser.repository.GameRepository;
import com.geoguesser.repository.PlayerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class GameService {

    private final GameRepository gameRepository;
    private final PlayerRepository playerRepository;
    private final LocationService locationService;
    private final ScoringService scoringService;
    private final PlayerService playerService;

    public GameService(GameRepository gameRepository,
                       PlayerRepository playerRepository,
                       LocationService locationService,
                       ScoringService scoringService,
                       PlayerService playerService) {
        this.gameRepository = gameRepository;
        this.playerRepository = playerRepository;
        this.locationService = locationService;
        this.scoringService = scoringService;
        this.playerService = playerService;
    }

    @Transactional
    public GuessResult processGuess(String username, GuessRequest guessRequest) {
        Player player = playerRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new RuntimeException("Player not found"));

        Location location = locationService.getById(guessRequest.getLocationId())
                .orElseThrow(() -> new RuntimeException("Location not found: " + guessRequest.getLocationId()));

        double distanceKm = scoringService.calculateDistance(
                location.getLatitude(),
                location.getLongitude(),
                guessRequest.getGuessLatitude(),
                guessRequest.getGuessLongitude()
        );

        int baseScore = scoringService.calculateScore(distanceKm);
        int finalScore = scoringService.applyTimeBonus(baseScore, guessRequest.getTimeTakenSeconds());

        Game game = buildGame(player, location, guessRequest, distanceKm, finalScore);
        gameRepository.save(game);

        playerService.updatePlayerStatistics(player, finalScore);
        locationService.incrementPlayCount(location.getId());

        return GuessResult.builder()
                .gameId(game.getId())
                .score(finalScore)
                .distanceKm(distanceKm)
                .actualLatitude(location.getLatitude())
                .actualLongitude(location.getLongitude())
                .country(location.getCountry())
                .city(location.getCity())
                .performanceLabel(scoringService.getPerformanceLabel(distanceKm))
                .build();
    }

    private Game buildGame(Player player, Location location, GuessRequest request, double distanceKm, int finalScore) {
        Game game = new Game(player, location);
        game.setGuessLatitude(request.getGuessLatitude());
        game.setGuessLongitude(request.getGuessLongitude());
        game.setDistanceKm(distanceKm);
        game.setScore(finalScore);
        game.setTimeTakenSeconds(request.getTimeTakenSeconds());
        game.setIsDailyChallenge(request.isDailyChallenge());
        return game;
    }

    public List<Game> getRecentGames(String username) {
        Player player = playerRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new RuntimeException("Player not found: " + username));
        return gameRepository.findByPlayerIdOrderByPlayedAtDesc(player.getId());
    }

    public Optional<Double> getAverageScore(Long playerId) {
        return Optional.ofNullable(gameRepository.findAverageScoreByPlayerId(playerId));
    }
}