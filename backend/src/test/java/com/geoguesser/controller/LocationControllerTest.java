package com.geoguesser.controller;

import com.geoguesser.model.Location;
import com.geoguesser.model.Location.Difficulty;
import com.geoguesser.service.LocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class LocationControllerTest {

    @Mock
    private LocationService locationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocationController controller = new LocationController(locationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/location/batch without excludeIds returns 200 and calls service with null exclusions")
    void testBatchWithoutExclusionsBackwardsCompatible() throws Exception {
        Location loc1 = new Location(48.8584, 2.2945, "France", "Paris", "http://image1", Difficulty.EASY);
        loc1.setId(1L);
        Location loc2 = new Location(51.5074, -0.1278, "UK", "London", "http://image2", Difficulty.EASY);
        loc2.setId(2L);

        when(locationService.getRandomLocationsWithExclusions(eq(5), eq(null)))
                .thenReturn(List.of(loc1, loc2));

        mockMvc.perform(get("/api/location/batch?count=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].imageUrl", is("http://image1")))
                .andExpect(jsonPath("$[1].id", is(2)))
                .andExpect(jsonPath("$[1].imageUrl", is("http://image2")));

        verify(locationService).getRandomLocationsWithExclusions(5, null);
    }

    @Test
    @DisplayName("GET /api/location/batch with excludeIds passes parsed IDs to service")
    void testBatchWithExclusions() throws Exception {
        Location loc = new Location(35.6762, 139.6503, "Japan", "Tokyo", "http://image3", Difficulty.MEDIUM);
        loc.setId(3L);

        when(locationService.getRandomLocationsWithExclusions(eq(5), eq(List.of(1L, 2L))))
                .thenReturn(List.of(loc));

        mockMvc.perform(get("/api/location/batch?count=5&excludeIds=1,2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(3)));

        verify(locationService).getRandomLocationsWithExclusions(5, List.of(1L, 2L));
    }
}
