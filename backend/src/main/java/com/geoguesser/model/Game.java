package com.geoguesser.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "games")
public class Game {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "player_id", nullable = false) private Player player;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "location_id", nullable = false) private Location location;
    @Column(name = "guess_latitude") private Double guessLatitude;
    @Column(name = "guess_longitude") private Double guessLongitude;
    @Column(name = "distance_km") private Double distanceKm;
    @Column(nullable = false) private Integer score = 0;
    @Column(name = "time_taken_seconds") private Integer timeTakenSeconds;
    @Column(name = "played_at") private LocalDateTime playedAt = LocalDateTime.now();
    @Column(name = "is_daily_challenge") private Boolean isDailyChallenge = false;

    public Game() {}
    public Game(Player player, Location location) { this.player=player; this.location=location; }

    public Long getId() { return id; }
    public void setGuessLatitude(Double v) { this.guessLatitude=v; }
    public void setGuessLongitude(Double v) { this.guessLongitude=v; }
    public void setDistanceKm(Double v) { this.distanceKm=v; }
    public void setScore(Integer v) { this.score=v; }
    public void setTimeTakenSeconds(Integer v) { this.timeTakenSeconds=v; }
    public void setIsDailyChallenge(Boolean v) { this.isDailyChallenge=v; }
}