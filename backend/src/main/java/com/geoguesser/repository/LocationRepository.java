package com.geoguesser.repository;

import com.geoguesser.model.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface LocationRepository extends JpaRepository<Location, Long> {

    // Get a truly random location from the DB (PostgreSQL syntax)
    @Query(value = "SELECT * FROM locations ORDER BY RANDOM() LIMIT 1", nativeQuery = true)
    Optional<Location> findRandomLocation();

    // Random location filtered by difficulty
    @Query(value = "SELECT * FROM locations WHERE difficulty = :difficulty ORDER BY RANDOM() LIMIT 1",
           nativeQuery = true)
    Optional<Location> findRandomLocationByDifficulty(String difficulty);

    // Get today's daily challenge (same location for everyone today)
    @Query(value = "SELECT * FROM locations WHERE id = (SELECT (EXTRACT(DOY FROM CURRENT_DATE)::INTEGER % COUNT(*)) + 1 FROM locations)",
           nativeQuery = true)
    Optional<Location> findDailyChallengeLocation();

    // Find by country (for country-streak mode)
    List<Location> findByCountryIgnoreCase(String country);

    // Get N random locations for a multi-round game
    @Query(value = "SELECT * FROM locations ORDER BY RANDOM() LIMIT :count", nativeQuery = true)
    List<Location> findRandomLocations(int count);
}
