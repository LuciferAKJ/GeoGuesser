package com.geoguesser.repository;

import com.geoguesser.model.Player;
import com.geoguesser.model.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {

    Optional<VerificationToken> findByTokenHash(String tokenHash);

    Optional<VerificationToken> findTopByPlayerOrderByCreatedAtDesc(Player player);

    List<VerificationToken> findAllByPlayerAndUsedAtIsNull(Player player);

    void deleteAllByPlayer(Player player);
}
