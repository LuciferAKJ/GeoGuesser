package com.geoguesser.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "games")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"player", "location"})
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @NotNull
    @Column(name = "guess_latitude")
    private Double guessLatitude;

    @NotNull
    @Column(name = "guess_longitude")
    private Double guessLongitude;

    @NotNull
    @Column(name = "distance_km")
    private Double distanceKm;

    @NotNull
    @Column(nullable = false)
    private Integer score = 0;

    @Column(name = "time_taken_seconds")
    private Integer timeTakenSeconds;

    @Column(name = "played_at", updatable = false)
    private LocalDateTime playedAt;

    @Column(name = "is_daily_challenge")
    private Boolean isDailyChallenge = false;

    public Game(Player player, Location location) {
        this.player = player;
        this.location = location;
    }

    @PrePersist
    public void prePersist() {
        if (playedAt == null) {
            playedAt = LocalDateTime.now();
        }
    }
}