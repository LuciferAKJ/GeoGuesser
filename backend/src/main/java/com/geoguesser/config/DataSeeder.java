package com.geoguesser.config;

import com.geoguesser.model.Location;
import com.geoguesser.model.Location.Difficulty;
import com.geoguesser.repository.LocationRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataSeeder implements CommandLineRunner {

    private final LocationRepository locationRepository;

    public DataSeeder(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    @Override
    public void run(String... args) {
        if (locationRepository.count() > 0) return;

        List<Location> locations = List.of(
                new Location(48.8584, 2.2945, "France", "Paris", null, Difficulty.EASY),
                new Location(40.7128, -74.0060, "USA", "New York", null, Difficulty.EASY),
                new Location(51.5074, -0.1278, "UK", "London", null, Difficulty.EASY),
                new Location(35.6762, 139.6503, "Japan", "Tokyo", null, Difficulty.MEDIUM),
                new Location(-33.8688, 151.2093, "Australia", "Sydney", null, Difficulty.MEDIUM),
                new Location(55.7558, 37.6173, "Russia", "Moscow", null, Difficulty.MEDIUM),
                new Location(-22.9068, -43.1729, "Brazil", "Rio de Janeiro", null, Difficulty.MEDIUM),
                new Location(1.3521, 103.8198, "Singapore", "Singapore", null, Difficulty.HARD),
                new Location(-1.2921, 36.8219, "Kenya", "Nairobi", null, Difficulty.HARD),
                new Location(64.1466, -21.9426, "Iceland", "Reykjavik", null, Difficulty.HARD)
        );

        locationRepository.saveAll(locations);
        System.out.println("Loaded default locations.");
    }
}