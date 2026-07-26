package com.app.badminton_backend.match.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable audit record created whenever an organizer removes a confirmed player
 * from a match. The MatchPlayer row is deleted (slot freed), but this log entry
 * persists forever for accountability and dispute resolution.
 */
@Data
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "match_player_removal_log")
public class MatchPlayerRemovalLog {

    @Id
    @GeneratedValue
    private UUID id;

    /** The match from which the player was removed. */
    @Column(nullable = false)
    private UUID matchId;

    /** The user who was removed. */
    @Column(nullable = false)
    private UUID removedUserId;

    /** The organizer who performed the removal. */
    @Column(nullable = false)
    private UUID removedByUserId;

    /** Timestamp of removal. */
    @Builder.Default
    private LocalDateTime removedAt = LocalDateTime.now();

    /** Optional reason supplied by the organizer (max 500 chars). */
    @Column(length = 500)
    private String reason;
}
