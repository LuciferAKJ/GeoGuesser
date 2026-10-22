package com.geoguesser.dto;

import java.time.LocalDateTime;

public class GameHistoryDTO {

    private Long gameId;
    private String country;
    private String city;
    private Integer score;
    private Double distanceKm;
    private Integer timeTakenSeconds;
    private LocalDateTime playedAt;
    private Boolean dailyChallenge;

    public GameHistoryDTO() {}

    public GameHistoryDTO(
            Long gameId,
            String country,
            String city,
            Integer score,
            Double distanceKm,
            Integer timeTakenSeconds,
            LocalDateTime playedAt,
            Boolean dailyChallenge) {

        this.gameId = gameId;
        this.country = country;
        this.city = city;
        this.score = score;
        this.distanceKm = distanceKm;
        this.timeTakenSeconds = timeTakenSeconds;
        this.playedAt = playedAt;
        this.dailyChallenge = dailyChallenge;
    }

    public Long getGameId() {
        return gameId;
    }

    public String getCountry() {
        return country;
    }

    public String getCity() {
        return city;
    }

    public Integer getScore() {
        return score;
    }

    public Double getDistanceKm() {
        return distanceKm;
    }

    public Integer getTimeTakenSeconds() {
        return timeTakenSeconds;
    }

    public LocalDateTime getPlayedAt() {
        return playedAt;
    }

    public Boolean getDailyChallenge() {
        return dailyChallenge;
    }
}