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
    private static final int PERFECT_RADIUS_KM = 1;    // <1km = perfect score
    private static final double SCORE_DECAY_FACTOR = 2.0; // score drop per km

    /**
     * Calculate distance between two coordinates using the Haversine formula.
     *
     * @return distance in kilometres
     */
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

    /**
     * Convert distance to a score (0-5000).
     *
     * Scoring tiers:
     *   0–1 km   → 5000 (perfect)
     *   1–10 km  → 4500–5000
     *   10–50 km → 3000–4500
     *   50–100 km → 2000–3000
     *   > 100 km → max(0, 5000 - distance * 2)
     */
    public int calculateScore(double distanceKm) {
        if (distanceKm <= PERFECT_RADIUS_KM) {
            return MAX_SCORE; // Perfect score
        } else if (distanceKm <= 10) {
            // Linear drop from 5000 to 4500
            return (int) (5000 - (distanceKm / 10.0) * 500);
        } else if (distanceKm <= 50) {
            // Linear drop from 4500 to 3000
            return (int) (4500 - ((distanceKm - 10) / 40.0) * 1500);
        } else if (distanceKm <= 100) {
            // Linear drop from 3000 to 2000
            return (int) (3000 - ((distanceKm - 50) / 50.0) * 1000);
        } else {
            // Steep decay for large distances
            int score = (int) (MAX_SCORE - (distanceKm * SCORE_DECAY_FACTOR));
            return Math.max(0, score);
        }
    }

    /**
     * Apply time bonus: faster guesses earn up to +500 extra points.
     * Full bonus under 10s, zero bonus at 120s+.
     */
    public int applyTimeBonus(int baseScore, int secondsTaken) {
        if (secondsTaken <= 0) return baseScore;
        if (secondsTaken >= 120) return baseScore;

        int timeBonus = (int) (500 * (1.0 - (secondsTaken / 120.0)));
        return baseScore + timeBonus;
    }

    /**
     * Get a human-readable performance label.
     */
    public String getPerformanceLabel(double distanceKm) {
        if (distanceKm <= 1)   return "🎯 Perfect!";
        if (distanceKm <= 10)  return "🔥 Excellent!";
        if (distanceKm <= 50)  return "😎 Great!";
        if (distanceKm <= 200) return "🙂 Good";
        if (distanceKm <= 500) return "😐 OK";
        return "😬 Far off";
    }
}
