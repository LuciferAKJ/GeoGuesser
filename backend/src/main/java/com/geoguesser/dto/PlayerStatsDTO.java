package com.geoguesser.dto;

public class PlayerStatsDTO {

    private String username;
    private Long totalScore;
    private Integer gamesPlayed;
    private Integer bestScore;
    private Double averageScore;

    public PlayerStatsDTO() {}

    public PlayerStatsDTO(
            String username,
            Long totalScore,
            Integer gamesPlayed,
            Integer bestScore,
            Double averageScore) {

        this.username = username;
        this.totalScore = totalScore;
        this.gamesPlayed = gamesPlayed;
        this.bestScore = bestScore;
        this.averageScore = averageScore;
    }

    public String getUsername() {
        return username;
    }

    public Long getTotalScore() {
        return totalScore;
    }

    public Integer getGamesPlayed() {
        return gamesPlayed;
    }

    public Integer getBestScore() {
        return bestScore;
    }

    public Double getAverageScore() {
        return averageScore;
    }
}