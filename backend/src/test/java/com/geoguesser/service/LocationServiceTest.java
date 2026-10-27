package com.geoguesser.service;

import com.geoguesser.model.Location;
import com.geoguesser.model.Location.Difficulty;
import com.geoguesser.repository.LocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

    @Mock
    private LocationRepository locationRepository;

    private LocationService locationService;

    private List<Location> sampleLocations;

    @BeforeEach
    void setUp() {
        locationService = new LocationService(locationRepository);
        ReflectionTestUtils.setField(locationService, "mapboxToken", "test-token");

        sampleLocations = new ArrayList<>();
        for (long i = 1; i <= 20; i++) {
            Location loc = new Location(
                    10.0 + i, 20.0 + i, "Country" + i, "City" + i,
                    "https://api.mapbox.com/test/" + i, Difficulty.MEDIUM
            );
            loc.setId(i);
            sampleLocations.add(loc);
        }
    }

    @Test
    @DisplayName("Batch without exclusions calls findRandomLocations and returns distinct locations")
    void testBatchWithoutExclusions() {
        List<Location> fiveLocations = sampleLocations.subList(0, 5);
        when(locationRepository.findRandomLocations(5)).thenReturn(fiveLocations);

        List<Location> result = locationService.getRandomLocations(5);

        assertEquals(5, result.size());
        verify(locationRepository).findRandomLocations(5);
        verify(locationRepository, never()).findRandomLocationsExcluding(anyList(), anyInt());

        Set<Long> uniqueIds = new HashSet<>();
        for (Location loc : result) {
            assertTrue(uniqueIds.add(loc.getId()), "Returned locations must have unique IDs");
        }
    }

    @Test
    @DisplayName("Batch with empty exclusion list safely calls findRandomLocations")
    void testBatchWithEmptyExclusions() {
        List<Location> fiveLocations = sampleLocations.subList(0, 5);
        when(locationRepository.findRandomLocations(5)).thenReturn(fiveLocations);

        List<Location> result = locationService.getRandomLocationsWithExclusions(5, Collections.emptyList());

        assertEquals(5, result.size());
        verify(locationRepository).findRandomLocations(5);
        verify(locationRepository, never()).findRandomLocationsExcluding(anyList(), anyInt());
    }

    @Test
    @DisplayName("Batch with active exclusion list excludes those IDs")
    void testBatchWithActiveExclusions() {
        List<Long> excludeIds = List.of(1L, 2L, 3L);
        List<Location> expected = sampleLocations.subList(3, 8); // IDs 4 to 8

        when(locationRepository.findRandomLocationsExcluding(eq(excludeIds), eq(5)))
                .thenReturn(expected);

        List<Location> result = locationService.getRandomLocationsWithExclusions(5, excludeIds);

        assertEquals(5, result.size());
        for (Location loc : result) {
            assertFalse(excludeIds.contains(loc.getId()), "Result must not contain any excluded ID");
        }
        verify(locationRepository).findRandomLocationsExcluding(eq(excludeIds), eq(5));
    }

    @Test
    @DisplayName("Fallback backfills without duplicating already-selected locations when eligible count is less than requested")
    void testFallbackWhenEligibleCountIsLessThanRequested() {
        List<Long> largeExclusionList = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
        // Only 2 eligible locations remain that are not excluded
        Location loc9 = sampleLocations.get(8);  // ID 9
        Location loc10 = sampleLocations.get(9); // ID 10

        when(locationRepository.findRandomLocationsExcluding(eq(largeExclusionList), eq(5)))
                .thenReturn(new ArrayList<>(List.of(loc9, loc10)));

        // Backfill needs 3 more locations. It must NOT pick loc9 or loc10.
        Location loc1 = sampleLocations.get(0); // ID 1
        Location loc2 = sampleLocations.get(1); // ID 2
        Location loc3 = sampleLocations.get(2); // ID 3
        when(locationRepository.findRandomLocationsExcluding(eq(List.of(9L, 10L)), eq(3)))
                .thenReturn(List.of(loc1, loc2, loc3));

        List<Location> result = locationService.getRandomLocationsWithExclusions(5, largeExclusionList);

        assertEquals(5, result.size(), "Result must satisfy requested count via fallback");

        // Verify zero duplicates
        Set<Long> uniqueIds = new HashSet<>();
        for (Location loc : result) {
            assertTrue(uniqueIds.add(loc.getId()), "Fallback must never introduce duplicate IDs");
        }
    }

    @Test
    @DisplayName("Fallback works when exclusions cover ALL locations in database")
    void testFallbackWhenExclusionsCoverAllLocations() {
        List<Long> allIds = sampleLocations.stream().map(Location::getId).toList();

        // No eligible locations returned
        when(locationRepository.findRandomLocationsExcluding(eq(allIds), eq(5)))
                .thenReturn(new ArrayList<>());

        // Backfill falls back to findRandomLocations(5)
        List<Location> fallbackLocations = sampleLocations.subList(0, 5);
        when(locationRepository.findRandomLocations(5))
                .thenReturn(fallbackLocations);

        List<Location> result = locationService.getRandomLocationsWithExclusions(5, allIds);

        assertEquals(5, result.size(), "Must fall back to random locations when all are excluded");
        verify(locationRepository).findRandomLocations(5);
    }

    @Test
    @DisplayName("Request with non-positive count returns empty list immediately")
    void testNonPositiveCount() {
        List<Location> resultZero = locationService.getRandomLocationsWithExclusions(0, List.of(1L));
        List<Location> resultNegative = locationService.getRandomLocationsWithExclusions(-1, null);

        assertTrue(resultZero.isEmpty());
        assertTrue(resultNegative.isEmpty());
        verifyNoInteractions(locationRepository);
    }

    @Test
    @DisplayName("buildMapboxImageUrl generates 1280x800@2x high-resolution URL by default")
    void testBuildMapboxImageUrlDefaultParameters() {
        String url = locationService.buildMapboxImageUrl(2.2945, 48.8584, 16);

        assertNotNull(url);
        assertTrue(url.startsWith("https://api.mapbox.com/styles/v1/mapbox/satellite-v9/static/"));
        assertTrue(url.contains("/2.294500,48.858400,16,0/1280x800@2x?access_token=test-token"));
    }

    @Test
    @DisplayName("buildMapboxImageUrl respects custom dimensions and retina toggle")
    void testBuildMapboxImageUrlCustomDimensionsAndRetina() {
        String urlNoRetina = locationService.buildMapboxImageUrl(12.4922, 41.8902, 15, 1024, 768, false);
        assertTrue(urlNoRetina.contains("/1024x768?access_token=test-token"));
        assertFalse(urlNoRetina.contains("@2x"));

        String urlRetina = locationService.buildMapboxImageUrl(12.4922, 41.8902, 15, 1024, 768, true);
        assertTrue(urlRetina.contains("/1024x768@2x?access_token=test-token"));
    }

    @Test
    @DisplayName("buildMapboxImageUrl clamps dimensions and zoom to valid Mapbox limits")
    void testBuildMapboxImageUrlClamping() {
        // Test upper bounds clamping (width > 1280, height > 1280, zoom > 22)
        String clampedHigh = locationService.buildMapboxImageUrl(0.0, 0.0, 25, 2000, 3000, true);
        assertTrue(clampedHigh.contains(",22,0/1280x1280@2x"));

        // Test lower bounds clamping (width <= 0, height <= 0, zoom < 0)
        String clampedLow = locationService.buildMapboxImageUrl(0.0, 0.0, -5, -100, 0, false);
        assertTrue(clampedLow.contains(",0,0/1x1?access_token="));
    }

    @Test
    @DisplayName("ensureImageUrl regenerates URL if null or blank")
    void testEnsureImageUrlForMissingUrl() {
        Location loc = new Location(2.2945, 48.8584, "France", "Paris", null, Difficulty.EASY, 16);
        when(locationRepository.save(any(Location.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Location refreshed = locationService.ensureImageUrl(loc);

        assertNotNull(refreshed.getImageUrl());
        assertTrue(refreshed.getImageUrl().contains("1280x800@2x"));
        assertTrue(refreshed.getImageUrl().contains(",16,0/"));
        verify(locationRepository).save(loc);
    }

    @Test
    @DisplayName("ensureImageUrl refreshes stale 800x500 URLs")
    void testEnsureImageUrlRefreshesStale800x500Url() {
        String oldUrl = "https://api.mapbox.com/styles/v1/mapbox/satellite-v9/static/2.2945,48.8584,13,0/800x500?access_token=test-token";
        Location loc = new Location(2.2945, 48.8584, "France", "Paris", oldUrl, Difficulty.EASY, 16);
        when(locationRepository.save(any(Location.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Location refreshed = locationService.ensureImageUrl(loc);

        assertNotEquals(oldUrl, refreshed.getImageUrl());
        assertTrue(refreshed.getImageUrl().contains("1280x800@2x"));
        assertTrue(refreshed.getImageUrl().contains(",16,0/"));
        verify(locationRepository).save(loc);
    }

    @Test
    @DisplayName("ensureImageUrl refreshes URLs missing @2x retina option")
    void testEnsureImageUrlRefreshesMissingRetinaUrl() {
        String urlWithoutRetina = "https://api.mapbox.com/styles/v1/mapbox/satellite-v9/static/2.294500,48.858400,16,0/1280x800?access_token=test-token";
        Location loc = new Location(2.2945, 48.8584, "France", "Paris", urlWithoutRetina, Difficulty.EASY, 16);
        when(locationRepository.save(any(Location.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Location refreshed = locationService.ensureImageUrl(loc);

        assertTrue(refreshed.getImageUrl().contains("@2x"));
        verify(locationRepository).save(loc);
    }

    @Test
    @DisplayName("ensureImageUrl preserves already valid high-resolution URLs without saving")
    void testEnsureImageUrlPreservesValidUrl() {
        String validUrl = locationService.buildMapboxImageUrl(2.2945, 48.8584, 16);
        Location loc = new Location(2.2945, 48.8584, "France", "Paris", validUrl, Difficulty.EASY, 16);

        Location result = locationService.ensureImageUrl(loc);

        assertEquals(validUrl, result.getImageUrl());
        verify(locationRepository, never()).save(any());
    }
}
