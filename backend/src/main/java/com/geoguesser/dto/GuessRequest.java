package com.geoguesser.dto;

public class GuessRequest {
    private Long locationId;
    private Double guessLatitude;
    private Double guessLongitude;
    private int timeTakenSeconds;
    private boolean dailyChallenge;

    public Long getLocationId() { return locationId; }
    public Double getGuessLatitude() { return guessLatitude; }
    public Double getGuessLongitude() { return guessLongitude; }
    public int getTimeTakenSeconds() { return timeTakenSeconds; }
    public boolean isDailyChallenge() { return dailyChallenge; }
    public void setLocationId(Long v) { this.locationId=v; }
    public void setGuessLatitude(Double v) { this.guessLatitude=v; }
    public void setGuessLongitude(Double v) { this.guessLongitude=v; }
    public void setTimeTakenSeconds(int v) { this.timeTakenSeconds=v; }
    public void setDailyChallenge(boolean v) { this.dailyChallenge=v; }
}