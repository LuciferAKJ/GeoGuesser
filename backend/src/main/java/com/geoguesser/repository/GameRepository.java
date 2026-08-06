package com.geoguesser.repository;

import com.geoguesser.model.Game;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface GameRepository extends JpaRepository<Game, Long> {

    List<Game> findByPlayerIdOrderByPlayedAtDesc(Long playerId);

    // Player's recent games (last 10)
    @Query("SELECT g FROM Game g WHERE g.player.id = :playerId ORDER BY g.playedAt DESC LIMIT 10")
    List<Game> findRecentGames(Long playerId);

    // Average score for a player
    @Query("SELECT AVG(g.score) FROM Game g WHERE g.player.id = :playerId")
    Double findAverageScoreByPlayerId(Long playerId);
}
