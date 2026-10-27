package com.geoguesser.config;

import com.geoguesser.model.Location;
import com.geoguesser.repository.LocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataSeederTest {

    @Mock
    private LocationRepository locationRepository;

    private DataSeeder dataSeeder;

    @BeforeEach
    void setUp() {
        dataSeeder = new DataSeeder(locationRepository);
    }

    @Test
    @DisplayName("Catalog contains at least 150 verified locations across all difficulties")
    void testCatalogSizeAndContent() {
        List<Location> catalog = LocationCatalog.getCatalog();
        assertTrue(catalog.size() >= 150, "Location catalog must have at least 150 locations, had: " + catalog.size());

        long easyCount = catalog.stream().filter(l -> l.getDifficulty() == Location.Difficulty.EASY).count();
        long mediumCount = catalog.stream().filter(l -> l.getDifficulty() == Location.Difficulty.MEDIUM).count();
        long hardCount = catalog.stream().filter(l -> l.getDifficulty() == Location.Difficulty.HARD).count();

        assertTrue(easyCount >= 40, "Must have at least 40 easy locations");
        assertTrue(mediumCount >= 40, "Must have at least 40 medium locations");
        assertTrue(hardCount >= 40, "Must have at least 40 hard locations");

        for (Location loc : catalog) {
            assertNotNull(loc.getLatitude(), "Latitude must not be null");
            assertNotNull(loc.getLongitude(), "Longitude must not be null");
            assertTrue(loc.getLatitude() >= -90.0 && loc.getLatitude() <= 90.0, "Latitude must be within valid range");
            assertTrue(loc.getLongitude() >= -180.0 && loc.getLongitude() <= 180.0,
                    "Longitude must be within valid range");
            assertNotNull(loc.getCountry(), "Country must not be null");
            assertNotNull(loc.getCity(), "City must not be null");
            assertFalse(loc.getCountry().isBlank(), "Country must not be blank");
            assertFalse(loc.getCity().isBlank(), "City must not be blank");
            assertNotNull(loc.getZoom(), "Zoom level must not be null");
            assertTrue(loc.getZoom() >= 10 && loc.getZoom() <= 20,
                    "Zoom must be reasonable (10-20), got: " + loc.getZoom());
        }
    }

    @Test
    @DisplayName("Empty database seeds all catalog locations")
    void testSeedEmptyDatabase() {
        when(locationRepository.findAll()).thenReturn(Collections.emptyList());

        int seededCount = dataSeeder.seedLocationsIfMissing();

        int catalogSize = LocationCatalog.getCatalog().size();
        assertEquals(catalogSize, seededCount);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Location>> captor = ArgumentCaptor.forClass(List.class);
        verify(locationRepository).saveAll(captor.capture());
        assertEquals(catalogSize, captor.getValue().size());
    }

    @Test
    @DisplayName("Database with all locations already present seeds 0 locations (idempotent)")
    void testSeedFullyPopulatedDatabaseIsIdempotent() {
        List<Location> catalog = LocationCatalog.getCatalog();
        when(locationRepository.findAll()).thenReturn(catalog);

        int seededCount = dataSeeder.seedLocationsIfMissing();

        assertEquals(0, seededCount, "Must not seed duplicate locations if all are already present");
        verify(locationRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("Database with partial locations seeds only the missing locations")
    void testSeedPartiallyPopulatedDatabase() {
        List<Location> catalog = LocationCatalog.getCatalog();
        // Suppose DB already has the first 10 locations
        List<Location> existingPartial = catalog.subList(0, 10);
        when(locationRepository.findAll()).thenReturn(existingPartial);

        int seededCount = dataSeeder.seedLocationsIfMissing();

        int expectedToSeed = catalog.size() - 10;
        assertEquals(expectedToSeed, seededCount, "Must only seed the missing locations");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Location>> captor = ArgumentCaptor.forClass(List.class);
        verify(locationRepository).saveAll(captor.capture());
        assertEquals(expectedToSeed, captor.getValue().size());
    }

    @Test
    @DisplayName("Existing locations with outdated zoom get updated from catalog")
    void testSyncZoomForExistingLocationsWithNullOrOldZoom() {
        Location catalogLoc = LocationCatalog.getCatalog().get(0); // e.g. Eiffel Tower, zoom 16
        // Existing DB row has outdated zoom = 13
        Location outdatedLoc = new Location(
                catalogLoc.getLatitude(), catalogLoc.getLongitude(),
                catalogLoc.getCountry(), catalogLoc.getCity(),
                "https://api.mapbox.com/old", catalogLoc.getDifficulty(), 13);
        outdatedLoc.setId(100L);

        when(locationRepository.findAll()).thenReturn(List.of(outdatedLoc));

        dataSeeder.seedLocationsIfMissing();

        assertEquals(catalogLoc.getZoom(), outdatedLoc.getZoom(), "Zoom must be updated to catalog value");
        verify(locationRepository, atLeastOnce()).saveAll(anyList());
    }
}