package com.geoguesser.repository;

import com.geoguesser.model.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LocationRepository extends JpaRepository<Location, Long> {

    // Get a truly random location from the DB (PostgreSQL native syntax)
    @Query(value = "SELECT * FROM locations ORDER BY RANDOM() LIMIT 1", nativeQuery = true)
    Optional<Location> findRandomLocation();

    // Random location filtered by difficulty
    @Query(value = "SELECT * FROM locations WHERE difficulty = :difficulty ORDER BY RANDOM() LIMIT 1",
            nativeQuery = true)
    Optional<Location> findRandomLocationByDifficulty(String difficulty);

    // Get today's daily challenge (same deterministic location for everyone today, robust to non-contiguous IDs and deletions)
    @Query(value = "SELECT l.* FROM locations l " +
                   "JOIN (" +
                   "    SELECT id, ROW_NUMBER() OVER (ORDER BY id) - 1 AS row_idx, COUNT(*) OVER () AS total_count " +
                   "    FROM locations" +
                   ") ranked ON l.id = ranked.id " +
                   "WHERE ranked.row_idx = (EXTRACT(DOY FROM CURRENT_DATE)::INTEGER % ranked.total_count) " +
                   "LIMIT 1",
            nativeQuery = true)
    Optional<Location> findDailyChallengeLocation();

    // Find by country (for country-streak mode)
    List<Location> findByCountryIgnoreCase(String country);

    // Get N random locations for a multi-round game
    @Query(value = "SELECT * FROM locations ORDER BY RANDOM() LIMIT :count", nativeQuery = true)
    List<Location> findRandomLocations(int count);

    long countByDifficulty(Location.Difficulty difficulty);

    List<Location> findTop20ByOrderByTimesPlayedAsc();
}