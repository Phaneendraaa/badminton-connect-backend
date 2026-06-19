package com.app.badminton_backend.match.enums;

public enum PostStatus {
    OPEN,      // accepting join requests
    FULL,      // all slots filled
    CANCELLED, // organizer cancelled
    EXPIRED    // scheduledAt has passed (set by scheduled job)
}
