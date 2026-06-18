package com.app.badminton_backend.elo;

import org.springframework.stereotype.Component;

@Component
public class EloCalculator {

    private static final int K_FACTOR = 32;

    /**
     * Returns the probability that player/team A wins, given both ratings.
     */
    public double expectedScore(int ratingA, int ratingB) {
        return 1.0 / (1.0 + Math.pow(10, (ratingB - ratingA) / 400.0));
    }

    /**
     * Returns how many points A should gain/lose.
     * actualScore = 1 if A won, 0 if A lost.
     */
    public int calculateEloChange(int ratingA, int ratingB, double actualScore) {
        double expected = expectedScore(ratingA, ratingB);
        return Math.round((float) (K_FACTOR * (actualScore - expected)));
    }
}
