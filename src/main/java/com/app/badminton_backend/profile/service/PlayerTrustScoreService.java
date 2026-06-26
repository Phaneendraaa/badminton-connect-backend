package com.app.badminton_backend.profile.service;

import com.app.badminton_backend.profile.entity.PlayerTrustScore;
import com.app.badminton_backend.profile.repository.PlayerTrustScoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Manages the PlayerTrustScore for each user.
 *
 * Pattern mirrors EloService.getOrCreate() exactly: lazy-create on first access,
 * never require callers to handle a missing row. This means score changes
 * (e.g. +1 on match complete, -10 on no-show) can be wired in later by
 * calling getOrCreate() then modifying the returned entity — no ProfileService
 * changes needed at that point.
 *
 * Score is capped at [0, 100] on every update.
 */
@Service
@RequiredArgsConstructor
public class PlayerTrustScoreService {

    private final PlayerTrustScoreRepository playerTrustScoreRepository;

    /**
     * Returns the PlayerTrustScore for userId, creating a default row
     * (score = 80) if none exists yet.
     *
     * Safe to call from read-only paths — the save only fires on the
     * very first access per user.
     */
    public PlayerTrustScore getOrCreate(UUID userId) {
        return playerTrustScoreRepository.findById(userId)
                .orElseGet(() -> playerTrustScoreRepository.save(
                        PlayerTrustScore.builder().userId(userId).build() // defaults to score=80
                ));
    }

    /**
     * Increments score by delta (positive = reward, negative = penalty).
     * Clamps result to [0, 100].
     */
    public PlayerTrustScore applyDelta(UUID userId, int delta) {
        PlayerTrustScore pts = getOrCreate(userId);
        int newScore = Math.max(0, Math.min(100, pts.getScore() + delta));
        pts.setScore(newScore);
        pts.setUpdatedAt(LocalDateTime.now());
        return playerTrustScoreRepository.save(pts);
    }

    /**
     * Records a no-show: increments noShowCount and applies -10 penalty.
     */
    public PlayerTrustScore recordNoShow(UUID userId) {
        PlayerTrustScore pts = getOrCreate(userId);
        pts.setNoShowCount(pts.getNoShowCount() + 1);
        pts.setUpdatedAt(LocalDateTime.now());
        int newScore = Math.max(0, pts.getScore() - 10);
        pts.setScore(newScore);
        return playerTrustScoreRepository.save(pts);
    }

    /**
     * Records a completed match with no no-show: +1 trust, increments matchesPlayed.
     */
    public PlayerTrustScore recordMatchCompleted(UUID userId) {
        PlayerTrustScore pts = getOrCreate(userId);
        pts.setMatchesPlayed(pts.getMatchesPlayed() + 1);
        int newScore = Math.min(100, pts.getScore() + 1);
        pts.setScore(newScore);
        pts.setUpdatedAt(LocalDateTime.now());
        return playerTrustScoreRepository.save(pts);
    }
}
