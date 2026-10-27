package com.geoguesser.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Entity
@Table(name = "locations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(nullable = false)
    private Double latitude;

    @NotNull
    @Column(nullable = false)
    private Double longitude;

    @NotBlank
    @Column(nullable = false)
    private String country;

    @NotBlank
    @Column(nullable = false)
    private String city;

    @Column(name = "image_url", length = 1024)
    private String imageUrl;

    @NotNull
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Difficulty difficulty = Difficulty.MEDIUM;

    @Column(name = "times_played")
    private Integer timesPlayed = 0;

    @Column(name = "zoom")
    private Integer zoom = 13;

    public enum Difficulty {
        EASY, MEDIUM, HARD
    }

    public Location(Double lat, Double lon, String country, String city, String imageUrl, Difficulty difficulty) {
        this(lat, lon, country, city, imageUrl, difficulty, 13);
    }

    public Location(Double lat, Double lon, String country, String city, String imageUrl, Difficulty difficulty, Integer zoom) {
        this.latitude = lat;
        this.longitude = lon;
        this.country = country;
        this.city = city;
        this.imageUrl = imageUrl;
        this.difficulty = difficulty;
        this.zoom = (zoom != null && zoom >= 1 && zoom <= 20) ? zoom : 13;
    }

    public Integer getZoom() {
        return zoom != null ? zoom : 13;
    }
}