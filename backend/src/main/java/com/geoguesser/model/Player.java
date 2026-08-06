package com.geoguesser.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "players")
public class Player {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true) private String username;
    @Column(nullable = false, unique = true) private String email;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Column(name = "total_score") private Long totalScore = 0L;
    @Column(name = "games_played") private Integer gamesPlayed = 0;
    @Column(name = "best_score") private Integer bestScore = 0;
    @Column(name = "created_at") private LocalDateTime createdAt = LocalDateTime.now();
    @OneToMany(mappedBy = "player", cascade = CascadeType.ALL, fetch = FetchType.LAZY) private List<Game> games;

    public Player() {}
    public Player(String username, String email, String passwordHash) {
        this.username=username; this.email=email; this.passwordHash=passwordHash;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Long getTotalScore() { return totalScore; }
    public Integer getGamesPlayed() { return gamesPlayed; }
    public Integer getBestScore() { return bestScore; }
    public void setTotalScore(Long v) { this.totalScore=v; }
    public void setGamesPlayed(Integer v) { this.gamesPlayed=v; }
    public void setBestScore(Integer v) { this.bestScore=v; }
}