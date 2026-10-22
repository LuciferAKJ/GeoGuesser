package com.geoguesser.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScoringServiceTest {

    private ScoringService scoringService;

    @BeforeEach
    void setUp() {
        scoringService = new ScoringService();
    }

    @Test
    @DisplayName("Perfect distance <= 1km awards 5000 points base score")
    void testPerfectDistance() {
        int score = scoringService.calculateScore(0.5);
        assertEquals(5000, score);
    }

    @Test
    @DisplayName("Time bonus cannot exceed 5000 points per round")
    void testTimeBonusCannotExceedMaxScore() {
        // Base score is 5000, 5 seconds taken gives time bonus
        int scoreWithBonus = scoringService.applyTimeBonus(5000, 5);
        assertEquals(5000, scoreWithBonus, "Round score must be strictly capped at 5000 points");
    }

    @Test
    @DisplayName("Time bonus adds points to non-perfect scores up to max 5000")
    void testTimeBonusAddsToSubmaximalScore() {
        int baseScore = 4000;
        int scoreWithBonus = scoringService.applyTimeBonus(baseScore, 10);
        assertTrue(scoreWithBonus > baseScore, "Time bonus should increase submaximal score");
        assertTrue(scoreWithBonus <= 5000, "Score with bonus must not exceed 5000");
    }

    @Test
    @DisplayName("Five perfect rounds with time bonus never exceed 25,000 total")
    void testFiveRoundMaxGameTotal() {
        int totalScore = 0;
        for (int i = 0; i < 5; i++) {
            int roundScore = scoringService.applyTimeBonus(5000, 1);
            assertEquals(5000, roundScore);
            totalScore += roundScore;
        }
        assertEquals(25000, totalScore, "5-round game total must not exceed 25,000");
    }

    @Test
    @DisplayName("Far distance calculates non-negative score")
    void testFarDistanceScoreDecay() {
        int score = scoringService.calculateScore(3000.0);
        assertTrue(score >= 0, "Score should never be negative");
    }

    @Test
    @DisplayName("Distance calculation between known coordinates")
    void testCalculateDistance() {
        // Distance between Paris (48.8584, 2.2945) and London (51.5074, -0.1278) is approx ~343 km
        double distance = scoringService.calculateDistance(48.8584, 2.2945, 51.5074, -0.1278);
        assertTrue(distance > 330 && distance < 360, "Distance between Paris and London should be ~343 km");
    }
}
