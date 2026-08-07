package com.geoguesser.repository;

import com.geoguesser.model.Game;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GameRepository extends JpaRepository<Game, Long> {

    List<Game> findByPlayerIdOrderByPlayedAtDesc(Long playerId);

    // Replaced invalid JPQL LIMIT query with a derived Spring Data query
    List<Game> findTop10ByPlayerIdOrderByPlayedAtDesc(Long playerId);

    List<Game> findByPlayerId(Long playerId);

    long countByPlayerId(Long playerId);

    // Average score for a player (Valid aggregate JPQL)
    @Query("SELECT AVG(g.score) FROM Game g WHERE g.player.id = :playerId")
    Double findAverageScoreByPlayerId(Long playerId);
}