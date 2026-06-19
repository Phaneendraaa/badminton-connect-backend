package com.app.badminton_backend.match.entity;

import com.app.badminton_backend.match.enums.JoinRequestStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Records a user's request to join an open match post.
 *
 * Lifecycle:
 *  PENDING  → ACCEPTED  (organizer accepts; MatchPlayer row created)
 *  PENDING  → REJECTED  (organizer rejects)
 *  PENDING  → CANCELLED (requester withdraws)
 *  ACCEPTED → (user calls leaveMatch; MatchPlayer row deleted, slots decremented)
 *
 * The auto-reject of all remaining PENDING requests when the post fills
 * is handled inside MatchJoinRequestService.acceptRequest() within the
 * same transaction — no dangling PENDING rows are ever left after a fill.
 */
@Data
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "match_join_request")
public class MatchJoinRequest {

    @Id
    @GeneratedValue
    private UUID id;

    /** The public post this request targets. */
    @Column(nullable = false)
    private UUID postId;

    /** The companion Match row linked to the post. Denormalized for convenience. */
    @Column(nullable = false)
    private UUID matchId;

    /** The user who wants to join. */
    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private JoinRequestStatus status = JoinRequestStatus.PENDING;

    /**
     * Snapshot of the user's ELO at request time.
     * Stored so the organizer can see the rating even if ELO changes later.
     */
    @Column(nullable = false)
    private Integer eloAtRequest;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Set when organizer accepts or rejects (or requester cancels).
     * Null while PENDING.
     */
    private LocalDateTime respondedAt;
}
