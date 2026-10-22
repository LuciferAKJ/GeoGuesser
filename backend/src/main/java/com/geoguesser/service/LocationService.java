package com.geoguesser.service;

import com.geoguesser.model.Location;
import com.geoguesser.repository.LocationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class LocationService {

    private final LocationRepository locationRepository;

    @Value("${mapbox.access-token}")
    private String mapboxToken;

    public LocationService(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

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

    private Location ensureImageUrl(Location location) {
        String currentUrl = location.getImageUrl();

        boolean needsRegeneration =
                currentUrl == null
                || currentUrl.isBlank()
                || currentUrl.contains("satellite-streets-v12")
                || currentUrl.contains("YOUR_EXISTING_MAPBOX_TOKEN");

        if (needsRegeneration) {
            String url = buildMapboxImageUrl(
                    location.getLongitude(),
                    location.getLatitude(),
                    13,
                    800,
                    500
            );

            location.setImageUrl(url);
            locationRepository.save(location);
        }

        return location;
    }

    // TODO: Move to MapboxService in later architectural expansion
    public String buildMapboxImageUrl(double lon, double lat, int zoom, int width, int height) {
        return String.format(
                "https://api.mapbox.com/styles/v1/mapbox/satellite-v9/static/" +
                "%.6f,%.6f,%d,0/%dx%d?access_token=%s",
                lon, lat, zoom, width, height, mapboxToken
        );
    }

    public void incrementPlayCount(Long locationId) {
        locationRepository.findById(locationId).ifPresent(loc -> {
            loc.setTimesPlayed(loc.getTimesPlayed() + 1);
            locationRepository.save(loc);
        });
    }
}