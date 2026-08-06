package com.geoguesser.model;

import jakarta.persistence.*;

@Entity
@Table(name = "locations")
public class Location {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Double latitude;
    @Column(nullable = false) private Double longitude;
    @Column(nullable = false) private String country;
    @Column(nullable = false) private String city;
    @Column(name = "image_url", length = 1024) private String imageUrl;
    @Column(nullable = false) @Enumerated(EnumType.STRING) private Difficulty difficulty = Difficulty.MEDIUM;
    @Column(name = "times_played") private Integer timesPlayed = 0;

    public enum Difficulty { EASY, MEDIUM, HARD }

    public Location() {}
    public Location(Double lat, Double lon, String country, String city, String imageUrl, Difficulty difficulty) {
        this.latitude=lat; this.longitude=lon; this.country=country;
        this.city=city; this.imageUrl=imageUrl; this.difficulty=difficulty;
    }

    public Long getId() { return id; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public String getCountry() { return country; }
    public String getCity() { return city; }
    public String getImageUrl() { return imageUrl; }
    public Difficulty getDifficulty() { return difficulty; }
    public Integer getTimesPlayed() { return timesPlayed; }
    public void setImageUrl(String v) { this.imageUrl=v; }
    public void setTimesPlayed(Integer v) { this.timesPlayed=v; }
}