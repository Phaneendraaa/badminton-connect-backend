package com.app.badminton_backend.match.repository;

import com.app.badminton_backend.match.entity.Notification;
import com.app.badminton_backend.match.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /** Paginated notifications for a user, newest first. */
    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /** Used by the MATCH_STARTING_SOON job to guard against duplicate firing. */
    Optional<Notification> findByUserIdAndRelatedMatchIdAndType(
            UUID userId, UUID relatedMatchId, NotificationType type);

    /** Unread count for the header badge. */
    long countByUserIdAndReadFalse(UUID userId);
}
