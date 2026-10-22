package com.geoguesser.service;

import com.geoguesser.dto.LeaderboardDTO;
import com.geoguesser.dto.PlayerStatsDTO;
import com.geoguesser.exception.EntityNotFoundException;
import com.geoguesser.model.Player;
import com.geoguesser.repository.GameRepository;
import com.geoguesser.repository.PlayerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final GameRepository gameRepository;

    public PlayerService(PlayerRepository playerRepository, GameRepository gameRepository) {
        this.playerRepository = playerRepository;
        this.gameRepository = gameRepository;
    }

    @Transactional
    public void updatePlayerStatistics(Player player, int finalScore) {
        player.setGamesPlayed(player.getGamesPlayed() + 1);
        player.setTotalScore(player.getTotalScore() + finalScore);

        if (finalScore > player.getBestScore()) {
            player.setBestScore(finalScore);
        }

        playerRepository.save(player);
    }

    public List<LeaderboardDTO> getTopLeaderboard() {
        List<Player> topPlayers = playerRepository.findTop10ByOrderByTotalScoreDesc();
        List<LeaderboardDTO> result = new ArrayList<>();

        for (int i = 0; i < topPlayers.size(); i++) {
            Player p = topPlayers.get(i);
            result.add(new LeaderboardDTO(
                    i + 1,
                    p.getUsername(),
                    p.getTotalScore(),
                    p.getGamesPlayed(),
                    p.getBestScore()
            ));
        }

        return result;
    }

    public PlayerStatsDTO getPlayerStats(String username) {
        Player p = playerRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new EntityNotFoundException("Player not found: " + username));

        Double averageScore = gameRepository.findAverageScoreByPlayerId(p.getId());

        if (averageScore == null) {
            averageScore = 0.0;
        }

        return new PlayerStatsDTO(
                p.getUsername(),
                p.getTotalScore(),
                p.getGamesPlayed(),
                p.getBestScore(),
                averageScore
        );
    }
}