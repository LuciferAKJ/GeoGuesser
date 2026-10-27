package com.geoguesser.controller;

import com.geoguesser.model.Location;
import com.geoguesser.service.LocationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/location")
@Validated
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

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

    @GetMapping("/daily")
    public ResponseEntity<?> getDailyChallenge() {
        return locationService.getDailyChallengeLocation()
                .map(loc -> ResponseEntity.ok(Map.of(
                        "id", loc.getId(),
                        "imageUrl", loc.getImageUrl()
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/batch")
    public ResponseEntity<List<Map<String, Object>>> getBatchLocations(
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int count,
            @RequestParam(required = false) List<Long> excludeIds) {

        List<Map<String, Object>> locations = locationService.getRandomLocationsWithExclusions(count, excludeIds)
                .stream()
                .map(loc -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("id", loc.getId());
                    map.put("imageUrl", loc.getImageUrl() != null ? loc.getImageUrl() : "");
                    return map;
                })
                .toList();

        return ResponseEntity.ok(locations);
    }

    @PostMapping
    public ResponseEntity<Location> addLocation(@RequestBody Location location) {
        return ResponseEntity.ok(locationService.save(location));
    }
}