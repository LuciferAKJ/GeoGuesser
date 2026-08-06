package com.geoguesser.repository;

import com.geoguesser.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerRepository extends JpaRepository<Player, Long> {

    Optional<Player> findByUsername(String username);
    Optional<Player> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    // Top 10 leaderboard by total score
    @Query("SELECT p FROM Player p ORDER BY p.totalScore DESC LIMIT 10")
    List<Player> findTop10ByOrderByTotalScoreDesc();
}
