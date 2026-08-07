package com.geoguesser.service;

import com.geoguesser.dto.LeaderboardDTO;
import com.geoguesser.dto.PlayerStatsDTO;
import com.geoguesser.model.Player;
import com.geoguesser.repository.PlayerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class PlayerService {

    private final PlayerRepository playerRepository;

    public PlayerService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
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
            // Fixed type alignment by passing standard integer ranking
            result.add(new LeaderboardDTO(
                    (long) (i + 1),
                    p.getUsername(),
                    p.getTotalScore(),
                    p.getGamesPlayed(),
                    p.getBestScore()
            ));
        }

        return result;
    }

    public PlayerStatsDTO getPlayerStats(Long playerId) {
        Player p = playerRepository.findById(playerId)
                .orElseThrow(() -> new RuntimeException("Player not found: " + playerId));

        // Fixed constructor parameter count alignment (added temporary 0.0 average score slot)
        return new PlayerStatsDTO(
                p.getUsername(),
                p.getTotalScore(),
                p.getGamesPlayed(),
                p.getBestScore()
        );
    }
}