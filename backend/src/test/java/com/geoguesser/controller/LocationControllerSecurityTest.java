package com.geoguesser.controller;

import com.geoguesser.config.JwtUtil;
import com.geoguesser.config.SecurityConfig;
import com.geoguesser.model.Location;
import com.geoguesser.model.Location.Difficulty;
import com.geoguesser.repository.PlayerRepository;
import com.geoguesser.service.LocationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies the actual security rules around the location controller. */
@WebMvcTest(LocationController.class)
@Import(SecurityConfig.class)
class LocationControllerSecurityTest {

    private static final String NEW_LOCATION_JSON =
            "{\"latitude\":1.0,\"longitude\":2.0,\"country\":\"X\",\"city\":\"Y\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LocationService locationService;

    @MockBean
    private PlayerRepository playerRepository;

    @MockBean
    private JwtUtil jwtUtil;

    @Test
    @DisplayName("Normal USER cannot create a location")
    @WithMockUser(username = "player", roles = "USER")
    void normalUserCannotCreateLocation() throws Exception {
        mockMvc.perform(post("/api/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_LOCATION_JSON))
                .andExpect(status().isForbidden());

        verify(locationService, never()).save(any());
    }

    @Test
    @DisplayName("Anonymous caller cannot create a location")
    void anonymousCannotCreateLocation() throws Exception {
        mockMvc.perform(post("/api/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_LOCATION_JSON))
                .andExpect(status().isForbidden());

        verify(locationService, never()).save(any());
    }

    @Test
    @DisplayName("GET /api/location/random remains public")
    void randomRemainsPublic() throws Exception {
        Location location = new Location(48.8584, 2.2945, "France", "Paris", "http://img", Difficulty.EASY);
        location.setId(1L);
        when(locationService.getRandomLocation()).thenReturn(Optional.of(location));

        mockMvc.perform(get("/api/location/random"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)));
    }

    @Test
    @DisplayName("GET /api/location/daily remains public")
    void dailyRemainsPublic() throws Exception {
        Location location = new Location(35.6762, 139.6503, "Japan", "Tokyo", "http://img", Difficulty.MEDIUM);
        location.setId(2L);
        when(locationService.getDailyChallengeLocation()).thenReturn(Optional.of(location));

        mockMvc.perform(get("/api/location/daily"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(2)));
    }

    @Test
    @DisplayName("GET /api/location/batch remains public")
    void batchRemainsPublic() throws Exception {
        Location location = new Location(51.5074, -0.1278, "UK", "London", "http://img", Difficulty.EASY);
        location.setId(3L);
        when(locationService.getRandomLocationsWithExclusions(anyInt(), any()))
                .thenReturn(List.of(location));

        mockMvc.perform(get("/api/location/batch?count=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }
}
