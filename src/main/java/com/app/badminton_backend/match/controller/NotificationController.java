package com.app.badminton_backend.match.controller;

import com.app.badminton_backend.match.dtos.NotificationDtoResponse;
import com.app.badminton_backend.match.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * GET /notifications?page=0&size=30
     * Paginated list, newest first.
     */
    @GetMapping
    public ResponseEntity<Page<NotificationDtoResponse>> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return ResponseEntity.ok(notificationService.getMyNotifications(page, size));
    }

    /**
     * PATCH /notifications/{id}/read
     * Marks a single notification as read.
     */
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id) {
        notificationService.markRead(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /notifications/unread-count
     * Returns {count: N} — used by the frontend header badge.
     */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        return ResponseEntity.ok(Map.of("count", notificationService.getUnreadCount()));
    }
}
