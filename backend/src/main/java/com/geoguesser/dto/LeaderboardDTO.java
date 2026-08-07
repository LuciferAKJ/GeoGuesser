package com.geoguesser.dto;

public class LeaderboardDTO {

    private int rank;
    private String username;
    private Long totalScore;
    private Integer gamesPlayed;
    private Integer bestScore;

    public LeaderboardDTO() {}

    public LeaderboardDTO(
            int rank,
            String username,
            Long totalScore,
            Integer gamesPlayed,
            Integer bestScore) {

        this.rank = rank;
        this.username = username;
        this.totalScore = totalScore;
        this.gamesPlayed = gamesPlayed;
        this.bestScore = bestScore;
    }

    public int getRank() {
        return rank;
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
}