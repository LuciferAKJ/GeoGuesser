package com.geoguesser.service;

import org.springframework.stereotype.Service;

/**
 * Handles all distance and score calculations.
 *
 * Haversine formula calculates the shortest distance over the
 * earth's surface between two lat/lon points.
 */
@Service
public class ScoringService {

    private static final int EARTH_RADIUS_KM = 6371;
    private static final int MAX_SCORE = 5000;
    private static final int EXCELLENT_SCORE = 4500;
    private static final int GREAT_SCORE = 3000;
    private static final int GOOD_SCORE = 2000;
    
    private static final int PERFECT_RADIUS_KM = 1;
    private static final double SCORE_DECAY_FACTOR = 2.0;

    public double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    public int calculateScore(double distanceKm) {
        if (distanceKm <= PERFECT_RADIUS_KM) {
            return MAX_SCORE;
        } else if (distanceKm <= 10) {
            return (int) (MAX_SCORE - (distanceKm / 10.0) * (MAX_SCORE - EXCELLENT_SCORE));
        } else if (distanceKm <= 50) {
            return (int) (EXCELLENT_SCORE - ((distanceKm - 10) / 40.0) * (EXCELLENT_SCORE - GREAT_SCORE));
        } else if (distanceKm <= 100) {
            return (int) (GREAT_SCORE - ((distanceKm - 50) / 50.0) * (GREAT_SCORE - GOOD_SCORE));
        } else {
            int score = (int) (MAX_SCORE - (distanceKm * SCORE_DECAY_FACTOR));
            return Math.max(0, score);
        }
    }

    public int applyTimeBonus(int baseScore, int secondsTaken) {
        if (secondsTaken <= 0 || secondsTaken >= 120) return baseScore;

        int timeBonus = (int) (500 * (1.0 - (secondsTaken / 120.0)));
        return baseScore + timeBonus;
    }

    public String getPerformanceLabel(double distanceKm) {
        if (distanceKm <= 1)   return "🎯 Perfect!";
        if (distanceKm <= 10)  return "🔥 Excellent!";
        if (distanceKm <= 50)  return "⭐ Amazing!";
        if (distanceKm <= 200) return "👏 Great!";
        if (distanceKm <= 500) return "🙂 Good!";
        return "🌍 World Explorer";
    }
}