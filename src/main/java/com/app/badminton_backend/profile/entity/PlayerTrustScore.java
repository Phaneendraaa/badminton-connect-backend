package com.app.badminton_backend.profile.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tracks a player's trust score and the signals that drive it.
 *
 * Default score is 80 — not 100. 100 is reserved for an exceptional
 * track record; 80 represents a brand-new account with no history.
 *
 * Score changes are wired in externally (TrustScoreService) at:
 *  - match completion with no no-show → +1 (capped at 100)
 *  - marked a no-show after being accepted → -10
 *
 * The entity exists now so ProfileService reads from here rather than
 * a hardcoded literal. Wiring real triggers in later requires no change
 * to ProfileService — only to PlayerTrustScoreService.
 */
@Data
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "player_trust_score")
public class PlayerTrustScore {

    @Id
    private UUID userId; // same UUID as User/Profile — 1-to-1

    @Column(nullable = false)
    @Builder.Default
    private Integer score = 80;

    @Column(nullable = false)
    @Builder.Default
    private Integer matchesPlayed = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer noShowCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer reportCount = 0;

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
