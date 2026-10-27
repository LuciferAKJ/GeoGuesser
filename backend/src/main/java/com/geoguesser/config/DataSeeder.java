package com.geoguesser.config;

import com.geoguesser.model.Location;
import com.geoguesser.repository.LocationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final LocationRepository locationRepository;

    public DataSeeder(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    @Override
    public void run(String... args) {
        seedLocationsIfMissing();
    }

    public int seedLocationsIfMissing() {
        List<Location> existing = locationRepository.findAll();
        Set<String> existingCoords = existing.stream()
                .map(l -> coordKey(l.getLatitude(), l.getLongitude()))
                .collect(Collectors.toSet());

        List<Location> catalog = LocationCatalog.getCatalog();
        List<Location> toInsert = catalog.stream()
                .filter(l -> !existingCoords.contains(coordKey(l.getLatitude(), l.getLongitude())))
                .toList();

        int inserted = 0;
        if (!toInsert.isEmpty()) {
            locationRepository.saveAll(toInsert);
            log.info("Seeded {} new locations. Total locations in database: {}",
                    toInsert.size(), existing.size() + toInsert.size());
            inserted = toInsert.size();
        } else {
            log.info("Location database is fully up to date. Total locations: {}", existing.size());
        }

        // Non-destructive zoom migration: sync per-location zoom levels from catalog for existing records
        Map<String, Location> catalogMap = catalog.stream()
                .collect(Collectors.toMap(l -> coordKey(l.getLatitude(), l.getLongitude()), l -> l, (a, b) -> a));
        List<Location> toUpdate = new ArrayList<>();
        for (Location ex : existing) {
            Location cat = catalogMap.get(coordKey(ex.getLatitude(), ex.getLongitude()));
            if (cat != null && cat.getZoom() != null && !cat.getZoom().equals(ex.getZoom())) {
                ex.setZoom(cat.getZoom());
                toUpdate.add(ex);
            }
        }
        if (!toUpdate.isEmpty()) {
            locationRepository.saveAll(toUpdate);
            log.info("Updated zoom level for {} existing locations from catalog.", toUpdate.size());
        }

        return inserted;
    }

    private String coordKey(Double lat, Double lon) {
        if (lat == null || lon == null) return "0.0000,0.0000";
        return String.format(Locale.ROOT, "%.4f,%.4f", lat, lon);
    }
}