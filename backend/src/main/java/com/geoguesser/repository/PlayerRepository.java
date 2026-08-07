package com.geoguesser.repository;

import com.geoguesser.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerRepository extends JpaRepository<Player, Long> {

    Optional<Player> findByUsername(String username);

    Optional<Player> findByUsernameIgnoreCase(String username);

    Optional<Player> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmail(String email);

    // Spring Data automatically handles LIMIT 10 via method naming conventions
    List<Player> findTop10ByOrderByTotalScoreDesc();
}