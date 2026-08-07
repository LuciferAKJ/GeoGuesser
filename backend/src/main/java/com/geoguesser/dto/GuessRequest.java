package com.geoguesser.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuessRequest {

    @NotNull(message = "Location ID cannot be null")
    private Long locationId;

    @NotNull(message = "Guess latitude cannot be null")
    @Min(value = -90, message = "Latitude must be >= -90")
    @Max(value = 90, message = "Latitude must be <= 90")
    private Double guessLatitude;

    @NotNull(message = "Guess longitude cannot be null")
    @Min(value = -180, message = "Longitude must be >= -180")
    @Max(value = 180, message = "Longitude must be <= 180")
    private Double guessLongitude;

    @Min(value = 0, message = "Time taken cannot be negative")
    private int timeTakenSeconds;

    private boolean dailyChallenge;
}