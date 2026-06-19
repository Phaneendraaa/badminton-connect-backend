package com.app.badminton_backend.match.entity;

import com.app.badminton_backend.match.enums.MatchType;
import com.app.badminton_backend.match.enums.PostStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a public matchmaking post. Creating a post simultaneously
 * creates a companion Match row (origin=OPEN). The post is the public
 * marketplace listing; the Match is the game record the rest of the
 * system understands.
 *
 * location is stored as plain text in v1. The column is wide enough
 * to later hold a JSON blob like {"name":"...", "lat":..., "lng":...}
 * without a breaking migration — just widen and parse.
 */
@Data
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "match_post")
public class MatchPost {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID creatorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchType matchType;

    /** Plain-text location in v1. Sized to accommodate future lat/lng JSON. */
    @Column(nullable = false, length = 500)
    private String location;

    @Column(nullable = false)
    private LocalDateTime scheduledAt;

    @Column(nullable = false)
    private Integer eloMin;

    @Column(nullable = false)
    private Integer eloMax;

    /**
     * Derived from matchType at creation time:
     * SINGLES → 2, DOUBLES → 4.
     * Stored so the value is stable even if matchType were ever patched.
     */
    @Column(nullable = false)
    private Integer slotsTotal;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PostStatus status = PostStatus.OPEN;

    /**
     * UUID of the companion Match row created at the same time as this post.
     * Never null after creation.
     */
    @Column(nullable = false)
    private UUID matchId;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Auto-expiry time. Default: scheduledAt + 2 hours.
     * A @Scheduled job flips status to EXPIRED once this passes.
     */
    private LocalDateTime expiresAt;
}
