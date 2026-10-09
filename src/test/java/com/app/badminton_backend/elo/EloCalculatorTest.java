package com.app.badminton_backend.elo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EloCalculatorTest {

    private final EloCalculator calculator = new EloCalculator();

    @Test
    void expectedScore_isHalf_whenRatingsAreEqual() {
        assertEquals(0.5, calculator.expectedScore(1000, 1000), 1e-9);
    }

    @Test
    void expectedScore_favoursHigherRatedPlayer() {
        assertTrue(calculator.expectedScore(1200, 1000) > 0.5);
        assertTrue(calculator.expectedScore(1000, 1200) < 0.5);
    }

    @Test
    void expectedScore_isComplementaryForBothSides() {
        double a = calculator.expectedScore(1350, 1100);
        double b = calculator.expectedScore(1100, 1350);
        assertEquals(1.0, a + b, 1e-9);
    }

    @Test
    void expectedScore_is91Percent_whenOnePlayerIs400PointsAhead() {
        // 1 / (1 + 10^(-1)) = 0.90909...
        assertEquals(0.9090909, calculator.expectedScore(1400, 1000), 1e-6);
    }

    @Test
    void eloChange_isPlusMinus16_forEqualRatings() {
        assertEquals(16, calculator.calculateEloChange(1000, 1000, 1.0));
        assertEquals(-16, calculator.calculateEloChange(1000, 1000, 0.0));
    }

    @Test
    void eloChange_isLargeForUpset() {
        // 1000 beats 1400: 32 * (1 - 0.0909) = 29.09 -> 29
        assertEquals(29, calculator.calculateEloChange(1000, 1400, 1.0));
    }

    @Test
    void eloChange_isSmallWhenFavouriteWins() {
        // 1400 beats 1000: 32 * (1 - 0.9091) = 2.91 -> 3
        assertEquals(3, calculator.calculateEloChange(1400, 1000, 1.0));
    }

    @Test
    void eloChange_isLargeNegativeWhenFavouriteLoses() {
        // 1400 loses to 1000: 32 * (0 - 0.9091) = -29.09 -> -29
        assertEquals(-29, calculator.calculateEloChange(1400, 1000, 0.0));
    }

    @Test
    void eloChange_isZeroSum_forEqualRatings() {
        int winnerGain = calculator.calculateEloChange(1100, 1100, 1.0);
        int loserLoss = calculator.calculateEloChange(1100, 1100, 0.0);
        assertEquals(0, winnerGain + loserLoss);
    }
}
