package com.app.badminton_backend.match.service;

import com.app.badminton_backend.auth.entity.User;
import com.app.badminton_backend.auth.repository.UserRepository;
import com.app.badminton_backend.auth.service.CurrentUserService;
import com.app.badminton_backend.exceptions.UnauthorizedActionException;
import com.app.badminton_backend.match.dtos.NotificationDtoResponse;
import com.app.badminton_backend.match.entity.Notification;
import com.app.badminton_backend.match.enums.NotificationType;
import com.app.badminton_backend.match.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Central notification service. All other services call create() — one
 * shared method rather than per-trigger ad hoc saves.
 *
 * Design intent:
 *  NotificationService is a "write-only" dependency for callers like
 *  MatchJoinRequestService and MatchChatService. They call create() and
 *  move on — no knowledge of the Notification entity needed in those services.
 *
 *  In addition to persisting a DB row (in-app notification), create() also
 *  fires an async Expo push notification to the recipient's device so they
 *  are alerted even when the app is backgrounded or closed.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final PushNotificationService pushNotificationService;
    private final SimpMessagingTemplate messagingTemplate;

    /** STOMP topic for per-user unread count updates. */
    private static final String NOTIF_TOPIC = "/topic/user/";

    /**
     * Creates a notification row AND fires an async push notification.
     *
     * Safe to call from within any @Transactional context — the DB save
     * participates in the caller's transaction, while the push is @Async
     * so it never blocks or rolls back the caller.
     */
    @Transactional
    public Notification create(UUID userId, NotificationType type,
                               UUID relatedPostId, UUID relatedMatchId, String message) {
        // Persist the in-app notification row (unchanged behaviour)
        Notification saved = notificationRepository.save(Notification.builder()
                .userId(userId)
                .type(type)
                .relatedPostId(relatedPostId)
                .relatedMatchId(relatedMatchId)
                .message(message)
                .build());

        // Broadcast updated unread count to the recipient's live session
        broadcastUnreadCount(userId);

        // Fire async push — no-ops silently if the user has no token stored
        firePush(userId, type, relatedPostId, relatedMatchId, message);

        return saved;
    }

    /**
     * Paginated notifications for the current user, newest first.
     */
    public Page<NotificationDtoResponse> getMyNotifications(int page, int size) {
        UUID userId = currentUserService.getCurrentUser().getId();
        return notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
                .map(this::toResponse);
    }

    /**
     * Marks a single notification as read.
     * Only the owning user may mark their own notifications read.
     */
    @Transactional
    public void markRead(UUID notificationId) {
        UUID userId = currentUserService.getCurrentUser().getId();
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));
        if (!notification.getUserId().equals(userId)) {
            throw new UnauthorizedActionException("You can only mark your own notifications as read");
        }
        notification.setRead(true);
        notificationRepository.save(notification);
        // Badge count just went down by one — push the new count live
        broadcastUnreadCount(userId);
    }

    /**
     * Unread count for the header badge — called by the frontend poll.
     */
    public long getUnreadCount() {
        UUID userId = currentUserService.getCurrentUser().getId();
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Sends the current unread count for a user to their personal STOMP topic.
     * Called after every notification create (count goes up) and markRead (count goes down).
     * Non-fatal if it fails.
     */
    private void broadcastUnreadCount(UUID userId) {
        try {
            long count = notificationRepository.countByUserIdAndReadFalse(userId);
            messagingTemplate.convertAndSend(
                    NOTIF_TOPIC + userId + "/notifications",
                    Map.of("unreadCount", count)
            );
        } catch (Exception ex) {
            // Non-fatal — just log; DB writes already succeeded
        }
    }

    /**
     * Looks up the recipient's stored push token and fires the push.
     * Called after every DB notification write — non-fatal if it fails.
     */
    private void firePush(UUID userId, NotificationType type,
                          UUID relatedPostId, UUID relatedMatchId, String message) {
        try {
            User recipient = userRepository.findById(userId).orElse(null);
            if (recipient == null || recipient.getPushToken() == null) return;

            String title = pushTitle(type);

            // Data payload allows the frontend to deep-link on tap
            Map<String, Object> data = new HashMap<>();
            data.put("type", type.name());
            if (relatedPostId  != null) data.put("postId",  relatedPostId.toString());
            if (relatedMatchId != null) data.put("matchId", relatedMatchId.toString());

            pushNotificationService.sendPush(recipient.getPushToken(), title, message, data);
        } catch (Exception ex) {
            // Non-fatal: push failure must never break the caller's transaction
        }
    }

    /** Maps NotificationType → a short, user-facing notification title. */
    private String pushTitle(NotificationType type) {
        return switch (type) {
            case JOIN_REQUEST_RECEIVED -> "New Join Request 🏸";
            case JOIN_REQUEST_ACCEPTED -> "Request Accepted ✅";
            case JOIN_REQUEST_REJECTED -> "Request Rejected";
            case POST_FULL             -> "Match is Full!";
            case POST_CANCELLED        -> "Match Cancelled";
            case MATCH_STARTING_SOON   -> "Match Starting Soon ⏰";
            case NEW_CHAT_MESSAGE      -> "New Message 💬";
        };
    }

    private NotificationDtoResponse toResponse(Notification n) {
        return NotificationDtoResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .relatedPostId(n.getRelatedPostId())
                .relatedMatchId(n.getRelatedMatchId())
                .message(n.getMessage())
                .read(n.getRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
