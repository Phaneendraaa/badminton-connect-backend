package com.app.badminton_backend.match.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Persists a single chat message for a Match.
 *
 * Access control (enforced in MatchChatService):
 *  - Only current MatchPlayers (and the organizer) may send or read messages.
 *  - A user who leaves the match (MatchJoinRequest.leaveMatch) has their
 *    MatchPlayer row deleted, so the service's player-lookup will return
 *    empty and they will receive a 403 for any new reads or sends.
 *    Historical messages are NOT deleted — they remain visible in the DB
 *    but the former participant cannot fetch them via the API.
 */
@Data
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "match_chat_message")
public class MatchChatMessage {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID matchId;

    @Column(nullable = false)
    private UUID senderId;

    @Column(nullable = false, length = 1000)
    private String content;

    @Builder.Default
    private LocalDateTime sentAt = LocalDateTime.now();
}
