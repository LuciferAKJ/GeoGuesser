package com.geoguesser.controller;

import com.geoguesser.model.Location;
import com.geoguesser.service.LocationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/location")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }
    /**
     * GET /api/location/random
     * Returns a random location. Only imageUrl and id are
     * safe to expose — latitude/longitude stay hidden until
     * the player submits their guess.
     */
    @GetMapping("/random")
    public ResponseEntity<?> getRandomLocation(
            @RequestParam(required = false) String difficulty) {

        var locationOpt = difficulty != null
                ? locationService.getRandomLocationByDifficulty(difficulty.toUpperCase())
                : locationService.getRandomLocation();

        return locationOpt
                .map(loc -> ResponseEntity.ok(Map.of(
                        "id", loc.getId(),
                        "imageUrl", loc.getImageUrl()
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/location/daily
     * Returns today's daily challenge location.
     */
    @GetMapping("/daily")
    public ResponseEntity<?> getDailyChallenge() {
        return locationService.getDailyChallengeLocation()
                .map(loc -> ResponseEntity.ok(Map.of(
                        "id", loc.getId(),
                        "imageUrl", loc.getImageUrl()
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/location/batch?count=5
     * Returns N random locations for a multi-round game.
     */
    @GetMapping("/batch")
    public ResponseEntity<List<Map<String, Object>>> getBatchLocations(
            @RequestParam(defaultValue = "5") int count) {

        List<Map<String, Object>> locations = locationService.getRandomLocations(count)
                .stream()
                .map(loc -> Map.<String, Object>of(
                        "id", loc.getId(),
                        "imageUrl", loc.getImageUrl()
                ))
                .toList();

        return ResponseEntity.ok(locations);
    }

    /**
     * POST /api/location (admin only — add new locations to the DB)
     */
    @PostMapping
    public ResponseEntity<Location> addLocation(@RequestBody Location location) {
        return ResponseEntity.ok(locationService.save(location));
    }
}
