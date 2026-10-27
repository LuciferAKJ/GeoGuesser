package com.geoguesser.service;

import com.geoguesser.model.Location;
import com.geoguesser.repository.LocationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

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
        return getRandomLocationsWithExclusions(count, null);
    }

    public List<Location> getRandomLocationsWithExclusions(int count, List<Long> excludeIds) {
        if (count <= 0) {
            return Collections.emptyList();
        }

        List<Long> cleanExcludeIds = (excludeIds != null)
                ? excludeIds.stream().filter(Objects::nonNull).distinct().toList()
                : Collections.emptyList();

        List<Location> selected = new ArrayList<>();

        if (!cleanExcludeIds.isEmpty()) {
            selected.addAll(locationRepository.findRandomLocationsExcluding(cleanExcludeIds, count));
        } else {
            selected.addAll(locationRepository.findRandomLocations(count));
        }

        // If exclusion list was too large and returned fewer than requested,
        // backfill with remaining distinct locations not already in selected
        if (selected.size() < count) {
            int needed = count - selected.size();
            List<Location> backfill;
            if (selected.isEmpty()) {
                backfill = locationRepository.findRandomLocations(needed);
            } else {
                List<Long> alreadySelectedIds = selected.stream()
                        .map(Location::getId)
                        .filter(Objects::nonNull)
                        .toList();
                backfill = locationRepository.findRandomLocationsExcluding(alreadySelectedIds, needed);
            }
            selected.addAll(backfill);
        }

        return selected.stream()
                .map(this::ensureImageUrl)
                .toList();
    }

    public static final int DEFAULT_WIDTH = 1280;
    public static final int DEFAULT_HEIGHT = 800;

    public Optional<Location> getById(Long id) {
        return locationRepository.findById(id);
    }

    public Location save(Location location) {
        return locationRepository.save(location);
    }

    Location ensureImageUrl(Location location) {
        String currentUrl = location.getImageUrl();
        int targetZoom = location.getZoom() != null ? location.getZoom() : 13;

        boolean needsRegeneration =
                currentUrl == null
                || currentUrl.isBlank()
                || currentUrl.contains("satellite-streets-v12")
                || currentUrl.contains("YOUR_EXISTING_MAPBOX_TOKEN")
                || currentUrl.contains("800x500")
                || !currentUrl.contains("@2x")
                || !currentUrl.contains("," + targetZoom + ",0/")
                || !currentUrl.contains(DEFAULT_WIDTH + "x" + DEFAULT_HEIGHT);

        if (needsRegeneration) {
            String url = buildMapboxImageUrl(
                    location.getLongitude(),
                    location.getLatitude(),
                    targetZoom,
                    DEFAULT_WIDTH,
                    DEFAULT_HEIGHT,
                    true
            );

            location.setImageUrl(url);
            locationRepository.save(location);
        }

        return location;
    }

    public String buildMapboxImageUrl(double lon, double lat, int zoom) {
        return buildMapboxImageUrl(lon, lat, zoom, DEFAULT_WIDTH, DEFAULT_HEIGHT, true);
    }

    public String buildMapboxImageUrl(double lon, double lat, int zoom, int width, int height) {
        return buildMapboxImageUrl(lon, lat, zoom, width, height, true);
    }

    public String buildMapboxImageUrl(double lon, double lat, int zoom, int width, int height, boolean retina) {
        // Clamp dimensions to Mapbox static API limits [1, 1280]
        int clampedWidth = Math.max(1, Math.min(1280, width));
        int clampedHeight = Math.max(1, Math.min(1280, height));
        int clampedZoom = Math.max(0, Math.min(22, zoom));
        String retinaSuffix = retina ? "@2x" : "";

        return String.format(
                Locale.ROOT,
                "https://api.mapbox.com/styles/v1/mapbox/satellite-v9/static/" +
                "%.6f,%.6f,%d,0/%dx%d%s?access_token=%s",
                lon, lat, clampedZoom, clampedWidth, clampedHeight, retinaSuffix, mapboxToken
        );
    }

    public void incrementPlayCount(Long locationId) {
        locationRepository.findById(locationId).ifPresent(loc -> {
            loc.setTimesPlayed(loc.getTimesPlayed() + 1);
            locationRepository.save(loc);
        });
    }
}