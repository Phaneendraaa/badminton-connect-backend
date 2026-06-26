package com.app.badminton_backend.match.service;

import com.app.badminton_backend.auth.service.CurrentUserService;
import com.app.badminton_backend.exceptions.UnauthorizedActionException;
import com.app.badminton_backend.match.dtos.NotificationDtoResponse;
import com.app.badminton_backend.match.entity.Notification;
import com.app.badminton_backend.match.enums.NotificationType;
import com.app.badminton_backend.match.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Central notification service. All other services call create() — one
 * shared method rather than per-trigger ad hoc saves.
 *
 * Design intent:
 *  NotificationService is a "write-only" dependency for callers like
 *  MatchJoinRequestService and MatchChatService. They call create() and
 *  move on — no knowledge of the Notification entity needed in those services.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final CurrentUserService currentUserService;

    /**
     * Creates a notification row. Safe to call from within any @Transactional
     * context — the save participates in the caller's transaction.
     */
    @Transactional
    public Notification create(UUID userId, NotificationType type,
                               UUID relatedPostId, UUID relatedMatchId, String message) {
        return notificationRepository.save(Notification.builder()
                .userId(userId)
                .type(type)
                .relatedPostId(relatedPostId)
                .relatedMatchId(relatedMatchId)
                .message(message)
                .build());
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
    }

    /**
     * Unread count for the header badge — called by the frontend poll.
     */
    public long getUnreadCount() {
        UUID userId = currentUserService.getCurrentUser().getId();
        return notificationRepository.countByUserIdAndReadFalse(userId);
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
