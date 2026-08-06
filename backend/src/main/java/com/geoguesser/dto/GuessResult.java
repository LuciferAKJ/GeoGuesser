package com.geoguesser.dto;

public class GuessResult {
    private Long gameId;
    private int score;
    private double distanceKm;
    private double actualLatitude;
    private double actualLongitude;
    private String country;
    private String city;
    private String performanceLabel;

    public static Builder builder() { return new Builder(); }
    public Long getGameId() { return gameId; }
    public int getScore() { return score; }
    public double getDistanceKm() { return distanceKm; }
    public double getActualLatitude() { return actualLatitude; }
    public double getActualLongitude() { return actualLongitude; }
    public String getCountry() { return country; }
    public String getCity() { return city; }
    public String getPerformanceLabel() { return performanceLabel; }

    public static class Builder {
        private GuessResult r = new GuessResult();
        public Builder gameId(Long v) { r.gameId=v; return this; }
        public Builder score(int v) { r.score=v; return this; }
        public Builder distanceKm(double v) { r.distanceKm=v; return this; }
        public Builder actualLatitude(double v) { r.actualLatitude=v; return this; }
        public Builder actualLongitude(double v) { r.actualLongitude=v; return this; }
        public Builder country(String v) { r.country=v; return this; }
        public Builder city(String v) { r.city=v; return this; }
        public Builder performanceLabel(String v) { r.performanceLabel=v; return this; }
        public GuessResult build() { return r; }
    }
}