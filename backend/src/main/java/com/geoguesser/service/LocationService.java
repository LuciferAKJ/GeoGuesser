package com.geoguesser.service;

import com.geoguesser.model.Location;
import com.geoguesser.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class LocationService {

    private final LocationRepository locationRepository;

    public LocationService(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }
    @Value("${mapbox.access-token}")
    private String mapboxToken;

    /**
     * Get a random location. The image URL is auto-generated from Mapbox
     * if not already stored in the database.
     */
    public Optional<Location> getRandomLocation() {
        return locationRepository.findRandomLocation()
                .map(this::ensureImageUrl);
    }

    public Optional<Location> getRandomLocationByDifficulty(String difficulty) {
        return locationRepository.findRandomLocationByDifficulty(difficulty)
                .map(this::ensureImageUrl);
    }

    public Optional<Location> getDailyChallengeLocation() {
        return locationRepository.findDailyChallengeLocation()
                .map(this::ensureImageUrl);
    }

    public List<Location> getRandomLocations(int count) {
        return locationRepository.findRandomLocations(count)
                .stream()
                .map(this::ensureImageUrl)
                .toList();
    }

    public Optional<Location> getById(Long id) {
        return locationRepository.findById(id);
    }

    public Location save(Location location) {
        return locationRepository.save(location);
    }

    /**
     * Generate a Mapbox Static Image URL for a location if none is stored.
     *
     * The URL format fetches a satellite/street hybrid map centred on the
     * location's coordinates at zoom level 13, 800×500 pixels.
     *
     * Docs: https://docs.mapbox.com/api/maps/static-images/
     */
    private Location ensureImageUrl(Location location) {
        if (location.getImageUrl() == null || location.getImageUrl().isBlank()) {
            String url = buildMapboxImageUrl(
                    location.getLongitude(),
                    location.getLatitude(),
                    13, 800, 500
            );
            location.setImageUrl(url);
            locationRepository.save(location);
        }
        return location;
    }

    public String buildMapboxImageUrl(double lon, double lat, int zoom, int width, int height) {
        // style: satellite-streets gives realistic imagery with labels
        return String.format(
            "https://api.mapbox.com/styles/v1/mapbox/satellite-streets-v12/static/" +
            "%.6f,%.6f,%d,0/%dx%d?access_token=%s",
            lon, lat, zoom, width, height, mapboxToken
        );
    }

    /**
     * Increment the play count for a location (analytics).
     */
    public void incrementPlayCount(Long locationId) {
        locationRepository.findById(locationId).ifPresent(loc -> {
            loc.setTimesPlayed(loc.getTimesPlayed() + 1);
            locationRepository.save(loc);
        });
    }
}
