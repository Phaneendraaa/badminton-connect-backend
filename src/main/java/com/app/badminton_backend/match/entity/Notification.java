package com.app.badminton_backend.match.entity;

import com.app.badminton_backend.match.enums.NotificationType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A single in-app notification for a user.
 *
 * Trigger points (all call NotificationService.create()):
 *  - MatchJoinRequestService.requestToJoin()    → JOIN_REQUEST_RECEIVED  (to organizer)
 *  - MatchJoinRequestService.acceptRequest()    → JOIN_REQUEST_ACCEPTED  (to requester)
 *  - MatchJoinRequestService.rejectRequest()    → JOIN_REQUEST_REJECTED  (to requester)
 *  - MatchPostService.acceptRequest() on fill   → POST_FULL              (to all players)
 *  - MatchPostService.cancelPost()              → POST_CANCELLED         (to all players)
 *  - MatchPostService.fireStartingSoonNotifs()  → MATCH_STARTING_SOON   (to all players)
 *  - MatchChatService.sendMessage()             → NEW_CHAT_MESSAGE       (to all others)
 */
@Data
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "notification", indexes = {
    @Index(name = "idx_notification_user_id", columnList = "userId"),
    @Index(name = "idx_notification_created_at", columnList = "createdAt")
})
public class Notification {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID userId; // recipient

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    private UUID relatedPostId;  // nullable — set when relevant
    private UUID relatedMatchId; // nullable — set when relevant

    @Column(nullable = false, length = 300)
    private String message;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean read = false;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
