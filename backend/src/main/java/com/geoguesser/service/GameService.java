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

    public GameService(GameRepository gameRepository,
                       PlayerRepository playerRepository,
                       LocationService locationService,
                       ScoringService scoringService) {
        this.gameRepository = gameRepository;
        this.playerRepository = playerRepository;
        this.locationService = locationService;
        this.scoringService = scoringService;
    }

    @Transactional
    public GuessResult processGuess(Long playerId, GuessRequest guessRequest) {

        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new RuntimeException("Player not found: " + playerId));

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

        Game game = new Game(player, location);
        game.setGuessLatitude(guessRequest.getGuessLatitude());
        game.setGuessLongitude(guessRequest.getGuessLongitude());
        game.setDistanceKm(distanceKm);
        game.setScore(finalScore);
        game.setTimeTakenSeconds(guessRequest.getTimeTakenSeconds());
        game.setIsDailyChallenge(guessRequest.isDailyChallenge());

        gameRepository.save(game);

        player.setGamesPlayed(player.getGamesPlayed() + 1);
        player.setTotalScore(player.getTotalScore() + finalScore);

        if (finalScore > player.getBestScore()) {
            player.setBestScore(finalScore);
        }

        playerRepository.save(player);

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

    public List<Game> getRecentGames(Long playerId) {
        return gameRepository.findRecentGames(playerId);
    }

    public Optional<Double> getAverageScore(Long playerId) {
        return Optional.ofNullable(gameRepository.findAverageScoreByPlayerId(playerId));
    }
}